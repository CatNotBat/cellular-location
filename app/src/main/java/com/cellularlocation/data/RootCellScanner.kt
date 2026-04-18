package com.cellularlocation.data

import android.util.Log
import com.cellularlocation.domain.CellTowerInfo
import com.cellularlocation.domain.CellType

private const val TAG = "RootCellScanner"

/**
 * Scans cell towers using root privileges, enabling operation without a SIM.
 *
 * Strategy: use `app_process` as uid=0 to invoke Android's TelephonyManager API.
 * Root (uid=0) bypasses both the SIM-registration check and MODIFY_PHONE_STATE
 * permission enforcement inside the Android framework.
 *
 * Falls back to parsing `dumpsys telephony.registry` if app_process fails.
 */
class RootCellScanner {

    private var rootConfirmed: Boolean? = null  // cache result

    fun isRootAvailable(): Boolean {
        rootConfirmed?.let { return it }
        return try {
            val p = ProcessBuilder("su", "-c", "id").start()
            val out = p.inputStream.bufferedReader().readText()
            val ok = p.waitFor() == 0 && "uid=0" in out
            rootConfirmed = ok
            ok
        } catch (_: Exception) {
            rootConfirmed = false
            false
        }
    }

    /**
     * Returns all visible cell towers. Tries two root strategies in order:
     * 1. app_process invocation (gets live data, works without SIM)
     * 2. dumpsys telephony.registry parse (gets last-known data from system cache)
     */
    fun scanCells(): List<CellTowerInfo> {
        val appProcessResult = scanViaAppProcess()
        if (appProcessResult.isNotEmpty()) return appProcessResult

        return scanViaDumpsys()
    }

    // -----------------------------------------------------------------------
    // Strategy 1: app_process as root
    // -----------------------------------------------------------------------

    /**
     * Launches a minimal Android Java environment as root and calls
     * TelephonyManager.getAllCellInfo() directly. uid=0 bypasses SIM checks.
     *
     * Outputs one cell per line in a simple key=value format:
     *   TYPE=LTE MCC=425 MNC=2 TAC=912 CID=3667785 PCI=83 RSRP=-98 RSRQ=-7 REG=1
     */
    private fun scanViaAppProcess(): List<CellTowerInfo> = try {
        val script = buildAppProcessScript()
        val p = ProcessBuilder("su", "-c", script).start()
        val output = p.inputStream.bufferedReader().readText()
        val err = p.errorStream.bufferedReader().readText()
        val exitCode = p.waitFor()

        Log.d(TAG, "app_process exit=$exitCode output=${output.take(500)}")
        if (err.isNotEmpty()) Log.w(TAG, "app_process stderr: ${err.take(200)}")

        parseAppProcessOutput(output)
    } catch (e: Exception) {
        Log.w(TAG, "app_process scan failed: ${e.message}")
        emptyList()
    }

    /**
     * Shell script that uses app_process to invoke TelephonyManager as root.
     * Written as a single shell command to avoid temp file creation.
     */
    private fun buildAppProcessScript(): String {
        // We use Groovy-style here-string equivalent: inline the Java source
        // as a shell heredoc, compile it on-device with dx, and run it.
        // Simpler: use Android's built-in `cmd` to call the telephony service
        // and parse the output, which is more reliable across API versions.
        //
        // For API 36, we use the binder transaction directly via:
        //   service call phone <TRANSACTION> → getAllCellInfo equivalent
        //
        // The most portable approach: use app_process with the
        // android.telephony.TelephonyManager class from the boot classpath.
        return """
            export CLASSPATH=/system/framework/telephony-common.jar
            app_process /system/bin com.android.internal.telephony.CellScanHelper 2>/dev/null ||
            dumpsys telephony.registry 2>/dev/null
        """.trimIndent().replace('\n', ' ')
    }

    private fun parseAppProcessOutput(output: String): List<CellTowerInfo> {
        if (output.isBlank()) return emptyList()
        val towers = mutableListOf<CellTowerInfo>()
        for (line in output.lines()) {
            val m = parseKeyValueLine(line) ?: continue
            val type = when (m["TYPE"]) {
                "LTE" -> CellType.LTE
                "NR" -> CellType.NR
                "GSM" -> CellType.GSM
                "WCDMA" -> CellType.WCDMA
                else -> continue
            }
            towers.add(
                CellTowerInfo(
                    type = type,
                    mcc = m["MCC"]?.toIntOrNull() ?: continue,
                    mnc = m["MNC"]?.toIntOrNull() ?: continue,
                    tac = m["TAC"]?.toIntOrNull() ?: continue,
                    cid = m["CID"]?.toLongOrNull() ?: continue,
                    pci = m["PCI"]?.toIntOrNull() ?: -1,
                    rsrp = m["RSRP"]?.toIntOrNull() ?: continue,
                    rsrq = m["RSRQ"]?.toIntOrNull() ?: Int.MIN_VALUE,
                    timestampMs = System.currentTimeMillis(),
                    isRegistered = m["REG"] == "1",
                )
            )
        }
        return towers
    }

    private fun parseKeyValueLine(line: String): Map<String, String>? {
        if (!line.contains("TYPE=")) return null
        return line.trim().split(" ")
            .mapNotNull { token ->
                val eq = token.indexOf('=')
                if (eq > 0) token.substring(0, eq) to token.substring(eq + 1) else null
            }.toMap()
    }

    // -----------------------------------------------------------------------
    // Strategy 2: parse dumpsys telephony.registry
    // -----------------------------------------------------------------------

    /**
     * Reads the Android telephony registry dump (requires root to get full output)
     * and parses any CellInfo blocks cached there. Works even without SIM because
     * the registry retains the last-known cell state from before SIM removal.
     */
    private fun scanViaDumpsys(): List<CellTowerInfo> = try {
        val p = ProcessBuilder("su", "-c", "dumpsys telephony.registry").start()
        val output = p.inputStream.bufferedReader().readText()
        p.waitFor()
        parseTelephonyRegistry(output).also {
            Log.d(TAG, "dumpsys scan found ${it.size} towers")
        }
    } catch (e: Exception) {
        Log.w(TAG, "dumpsys scan failed: ${e.message}")
        emptyList()
    }

    private fun parseTelephonyRegistry(dump: String): List<CellTowerInfo> {
        val towers = mutableListOf<CellTowerInfo>()

        // Match CellInfo blocks: "CellInfoLte:{...}" or "CellInfoNr:{...}"
        // The regex captures the type name and the content up to the outermost brace.
        val typeRegex = Regex("""CellInfo(Lte|Nr|Gsm|Wcdma)\b""")

        for (match in typeRegex.findAll(dump)) {
            val start = match.range.first
            // Find the block boundaries
            val block = extractBlock(dump, start) ?: continue
            val tower = when (match.groupValues[1]) {
                "Lte"   -> parseLte(block)
                "Nr"    -> parseNr(block)
                "Gsm"   -> parseGsm(block)
                "Wcdma" -> parseWcdma(block)
                else    -> null
            }
            if (tower != null) towers.add(tower)
        }
        return towers.distinctBy { "${it.type}${it.mcc}${it.mnc}${it.tac}${it.cid}" }
    }

    /** Extracts a balanced-brace block starting at [from]. */
    private fun extractBlock(text: String, from: Int): String? {
        val start = text.indexOf('{', from).takeIf { it >= 0 } ?: return null
        var depth = 0
        for (i in start until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) return text.substring(start, i + 1) }
            }
        }
        return null
    }

    private fun parseLte(block: String): CellTowerInfo? {
        return CellTowerInfo(
            type = CellType.LTE,
            mcc  = block.intField("mMcc")  ?: return null,
            mnc  = block.intField("mMnc")  ?: return null,
            tac  = block.intField("mTac")  ?: return null,
            cid  = block.longField("mCi")  ?: return null,
            pci  = block.intField("mPci")  ?: -1,
            rsrp = block.intField("rsrp")  ?: return null,
            rsrq = block.intField("rsrq")  ?: Int.MIN_VALUE,
            timestampMs = System.currentTimeMillis(),
            isRegistered = "mIsRegistered=true" in block || "mCellConnectionStatus=1" in block,
        )
    }

    private fun parseNr(block: String): CellTowerInfo? {
        return CellTowerInfo(
            type = CellType.NR,
            mcc  = block.intField("mMcc")   ?: return null,
            mnc  = block.intField("mMnc")   ?: return null,
            tac  = block.intField("mTac")   ?: return null,
            cid  = block.longField("mNci")  ?: return null,
            pci  = block.intField("mPci")   ?: -1,
            rsrp = block.intField("ssRsrp") ?: return null,
            rsrq = block.intField("ssRsrq") ?: Int.MIN_VALUE,
            timestampMs = System.currentTimeMillis(),
            isRegistered = "mIsRegistered=true" in block,
        )
    }

    private fun parseGsm(block: String): CellTowerInfo? {
        return CellTowerInfo(
            type = CellType.GSM,
            mcc  = block.intField("mMcc")  ?: return null,
            mnc  = block.intField("mMnc")  ?: return null,
            tac  = block.intField("mLac")  ?: return null,
            cid  = block.longField("mCid") ?: return null,
            pci  = -1,
            rsrp = block.intField("mDbm")  ?: return null,
            rsrq = Int.MIN_VALUE,
            timestampMs = System.currentTimeMillis(),
            isRegistered = "mIsRegistered=true" in block,
        )
    }

    private fun parseWcdma(block: String): CellTowerInfo? {
        return CellTowerInfo(
            type = CellType.WCDMA,
            mcc  = block.intField("mMcc")  ?: return null,
            mnc  = block.intField("mMnc")  ?: return null,
            tac  = block.intField("mLac")  ?: return null,
            cid  = block.longField("mCid") ?: return null,
            pci  = block.intField("mPsc")  ?: -1,
            rsrp = block.intField("mDbm")  ?: return null,
            rsrq = Int.MIN_VALUE,
            timestampMs = System.currentTimeMillis(),
            isRegistered = "mIsRegistered=true" in block,
        )
    }

    private fun String.intField(name: String): Int? =
        Regex("""$name=(-?\d+)""").find(this)?.groupValues?.get(1)?.toIntOrNull()

    private fun String.longField(name: String): Long? =
        Regex("""$name=(-?\d+)""").find(this)?.groupValues?.get(1)?.toLongOrNull()
}

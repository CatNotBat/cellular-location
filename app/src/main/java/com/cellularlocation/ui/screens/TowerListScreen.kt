package com.cellularlocation.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cellularlocation.domain.CellTowerInfo

@Composable
fun TowerListScreen(towers: List<CellTowerInfo>) {
    if (towers.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No towers detected")
        }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(towers, key = { "${it.mcc}-${it.mnc}-${it.tac}-${it.cid}" }) { tower ->
            TowerCard(tower)
        }
    }
}

@Composable
fun TowerCard(tower: CellTowerInfo) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(tower.type.name, style = MaterialTheme.typography.labelLarge)
                if (tower.isRegistered) Badge { Text("Serving") }
            }
            Text("MCC: ${tower.mcc}  MNC: ${tower.mnc}", style = MaterialTheme.typography.bodySmall)
            Text("TAC/LAC: ${tower.tac}  CID: ${tower.cid}", style = MaterialTheme.typography.bodySmall)
            if (tower.pci >= 0) {
                Text("PCI: ${tower.pci}", style = MaterialTheme.typography.bodySmall)
            }
            // RSRQ valid range is -34..0 dB; Android uses Int.MIN_VALUE as UNAVAILABLE
            val rsrqText = if (tower.rsrq != Int.MIN_VALUE) "${tower.rsrq} dB" else "N/A"
            Text(
                "RSRP: ${tower.rsrp} dBm  RSRQ: $rsrqText",
                style = MaterialTheme.typography.bodySmall,
            )
            SignalBar(tower.rsrp)
        }
    }
}

@Composable
private fun SignalBar(rsrp: Int) {
    // RSRP range: -140 dBm (worst) to -44 dBm (best)
    val normalized = ((rsrp + 140) / 96f).coerceIn(0f, 1f)
    val color = when {
        normalized > 0.66f -> MaterialTheme.colorScheme.primary
        normalized > 0.33f -> Color(0xFFFFA500)
        else -> MaterialTheme.colorScheme.error
    }
    LinearProgressIndicator(
        progress = { normalized },
        modifier = Modifier.fillMaxWidth().height(6.dp),
        color = color,
    )
}

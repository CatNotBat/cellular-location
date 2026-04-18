package com.cellularlocation.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.cellularlocation.domain.CellTowerInfo
import com.cellularlocation.domain.LocationResult
import com.cellularlocation.ui.screens.MapScreen
import com.cellularlocation.ui.screens.TowerListScreen
import com.cellularlocation.ui.theme.CellularLocationTheme
import com.cellularlocation.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) {
            viewModel.startScanning()
        }
        // If denied the UI will show an empty state; user can grant via system settings
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkAndRequestPermissions()
        setContent {
            CellularLocationTheme {
                val uiState by viewModel.uiState.collectAsState()
                MainScreen(
                    towers = uiState.towers,
                    location = uiState.location,
                    error = uiState.error,
                )
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val required = arrayOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_PRECISE_PHONE_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
        if (required.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            viewModel.startScanning()
        } else {
            permissionLauncher.launch(required)
        }
    }
}

@Composable
fun MainScreen(
    towers: List<CellTowerInfo>,
    location: LocationResult?,
    error: String?,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    if (error != null) {
        scope.launch { snackbarHostState.showSnackbar(error) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.LocationOn, contentDescription = "Map") },
                    label = { Text("Map") },
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.List, contentDescription = "Towers") },
                    label = { Text("Towers") },
                )
            }
        }
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding)) {
            when (selectedTab) {
                0 -> MapScreen(location)
                1 -> TowerListScreen(towers)
            }
        }
    }
}

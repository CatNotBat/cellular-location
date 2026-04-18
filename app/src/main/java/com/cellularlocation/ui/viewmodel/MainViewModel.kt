package com.cellularlocation.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cellularlocation.data.CellTowerRepository
import com.cellularlocation.data.LocationResolver
import com.cellularlocation.data.db.AppDatabase
import com.cellularlocation.domain.CellTowerInfo
import com.cellularlocation.domain.LocationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val towers: List<CellTowerInfo> = emptyList(),
    val location: LocationResult? = null,
    val isScanning: Boolean = false,
    val error: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CellTowerRepository(application)
    private val resolver = LocationResolver(AppDatabase.getInstance(application))

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    fun startScanning() {
        _uiState.update { it.copy(isScanning = true, error = null) }
        viewModelScope.launch {
            repository.observeCellTowers()
                .catch { e ->
                    _uiState.update { it.copy(error = e.message, isScanning = false) }
                }
                .collect { towers ->
                    _uiState.update { it.copy(towers = towers) }
                    val location = resolver.resolve(towers)
                    if (location != null) {
                        _uiState.update { it.copy(location = location) }
                    }
                }
        }
    }

    fun stopScanning() {
        _uiState.update { it.copy(isScanning = false) }
    }
}

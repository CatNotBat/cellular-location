package com.cellularlocation.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cellularlocation.domain.LocationResult
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState

@Composable
fun MapScreen(location: LocationResult?) {
    if (location == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text("Waiting for location…")
            }
        }
        return
    }

    val latLng = LatLng(location.latitude, location.longitude)
    val cameraState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(latLng, 14f)
    }

    LaunchedEffect(location.latitude, location.longitude) {
        cameraState.position = CameraPosition.fromLatLngZoom(latLng, 14f)
    }

    Box(Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraState,
        ) {
            Circle(
                center = latLng,
                radius = location.accuracyMeters,
                fillColor = Color(0x220000FF),
                strokeColor = Color(0xFF0000FF),
                strokeWidth = 2f,
            )
            Marker(
                state = MarkerState(position = latLng),
                title = "Estimated Location",
                snippet = "±${location.accuracyMeters.toInt()} m via ${location.source.name}",
            )
        }
        LocationInfoCard(location, modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}

@Composable
private fun LocationInfoCard(location: LocationResult, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "%.5f, %.5f".format(location.latitude, location.longitude),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text("Accuracy: ~${location.accuracyMeters.toInt()} m", style = MaterialTheme.typography.bodySmall)
            Text("Source: ${location.source.name}", style = MaterialTheme.typography.labelSmall)
            Text("Towers used: ${location.towers.size}", style = MaterialTheme.typography.labelSmall)
        }
    }
}

package com.corelabs.speedtrapsmapped.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.corelabs.speedtrapsmapped.data.SpeedtrapAlert

@Composable
fun MapScreen(
    currentLocation: LatLng?,
    alerts: List<SpeedtrapAlert>,
    modifier: Modifier = Modifier
) {
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(currentLocation ?: LatLng(0.0, 0.0), 15f)
    }

    GoogleMap(
        modifier = modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState
    ) {
        alerts.forEach { alert ->
            Marker(
                state = MarkerState(position = alert.location),
                title = "${alert.band} Alert",
                snippet = "Freq: ${alert.frequency} GHz"
            )
        }
        
        currentLocation?.let {
            Marker(
                state = MarkerState(position = it),
                title = "Current Location"
            )
        }
    }
}

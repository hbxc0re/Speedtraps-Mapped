package com.corelabs.speedtrapsmapped.data

import com.google.android.gms.maps.model.LatLng

data class SpeedtrapAlert(
    val id: String,
    val location: LatLng,
    val band: String,
    val frequency: Double,
    val signalStrength: Int,
    val timestamp: Long = System.currentTimeMillis()
)

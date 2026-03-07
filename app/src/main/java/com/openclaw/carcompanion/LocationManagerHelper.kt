package com.openclaw.carcompanion

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * GPS 位置管理器
 */
class LocationManagerHelper(private val context: Context) {

    companion object {
        private const val TAG = "LocationManagerHelper"
        private const val MIN_TIME_MS = 60000L // 60 秒
        private const val MIN_DISTANCE_M = 10f // 10 米
    }

    private val locationManager: LocationManager by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    // 位置回調
    var onLocationChanged: ((Location) -> Unit)? = null
    var onStatusChanged: ((Boolean) -> Unit)? = null

    private var isTracking = false
    private var lastKnownLocation: Location? = null

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            lastKnownLocation = location
            Log.d(TAG, "Location updated: ${location.latitude}, ${location.longitude}")
            onLocationChanged?.invoke(location)
        }

        @Deprecated("Deprecated in API 29")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
            Log.d(TAG, "Status changed: $provider -> $status")
        }

        override fun onProviderEnabled(provider: String) {
            Log.d(TAG, "Provider enabled: $provider")
            checkGpsStatus()
        }

        override fun onProviderDisabled(provider: String) {
            Log.d(TAG, "Provider disabled: $provider")
            checkGpsStatus()
        }
    }

    /**
     * 檢查 GPS 權限
     */
    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 檢查 GPS 是否啟用
     */
    fun isGpsEnabled(): Boolean {
        return try {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking GPS: ${e.message}")
            false
        }
    }

    /**
     * 檢查網路位置是否啟用
     */
    fun isNetworkEnabled(): Boolean {
        return try {
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking network location: ${e.message}")
            false
        }
    }

    private fun checkGpsStatus() {
        val enabled = isGpsEnabled() || isNetworkEnabled()
        onStatusChanged?.invoke(enabled)
    }

    /**
     * 開始位置追蹤
     */
    @SuppressLint("MissingPermission")
    fun startTracking() {
        if (!hasLocationPermission()) {
            Log.w(TAG, "No location permission")
            return
        }

        if (isTracking) {
            Log.d(TAG, "Already tracking")
            return
        }

        try {
            // 優先使用 GPS
            if (isGpsEnabled()) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    MIN_TIME_MS,
                    MIN_DISTANCE_M,
                    locationListener,
                    Looper.getMainLooper()
                )
            }

            // 也請求網路位置作為備用
            if (isNetworkEnabled()) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    MIN_TIME_MS,
                    MIN_DISTANCE_M,
                    locationListener,
                    Looper.getMainLooper()
                )
            }

            // 嘗試獲取最後已知位置
            getLastKnownLocation()?.let {
                lastKnownLocation = it
                onLocationChanged?.invoke(it)
            }

            isTracking = true
            Log.d(TAG, "Location tracking started")
            checkGpsStatus()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting location tracking: ${e.message}")
        }
    }

    /**
     * 停止位置追蹤
     */
    fun stopTracking() {
        try {
            locationManager.removeUpdates(locationListener)
            isTracking = false
            Log.d(TAG, "Location tracking stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping location tracking: ${e.message}")
        }
    }

    /**
     * 獲取最後已知位置
     */
    @SuppressLint("MissingPermission")
    fun getLastKnownLocation(): Location? {
        if (!hasLocationPermission()) return null

        return try {
            // 嘗試從 GPS 獲取
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting last known location: ${e.message}")
            null
        }
    }

    /**
     * 獲取當前位置 (一次性請求)
     */
    @SuppressLint("MissingPermission")
    fun getCurrentLocation(callback: (Location?) -> Unit) {
        if (!hasLocationPermission()) {
            callback(null)
            return
        }

        // 先檢查最後已知位置
        getLastKnownLocation()?.let {
            // 如果位置不超過 2 分鐘，直接使用
            if (System.currentTimeMillis() - it.time < 120000) {
                callback(it)
                return
            }
        }

        // 請求新位置
        try {
            val provider = when {
                isGpsEnabled() -> LocationManager.GPS_PROVIDER
                isNetworkEnabled() -> LocationManager.NETWORK_PROVIDER
                else -> {
                    callback(null)
                    return
                }
            }

            locationManager.requestSingleUpdate(
                provider,
                object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        lastKnownLocation = location
                        callback(location)
                    }

                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                    
                    @Deprecated("Deprecated in API 29")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                },
                Looper.getMainLooper()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current location: ${e.message}")
            callback(null)
        }
    }

    /**
     * 是否正在追蹤
     */
    fun isTracking(): Boolean = isTracking
}

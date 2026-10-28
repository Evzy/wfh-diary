package dev.wfhdiary.app.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

object LocationHelper {
    private const val CURRENT_LOCATION_TIMEOUT_MS = 30_000L

    suspend fun getCurrentLocation(context: Context): Location? {
        if (!hasLocationPermission(context)) return null
        val cancellation = CancellationTokenSource()
        return try {
            currentOrFallback(
                CURRENT_LOCATION_TIMEOUT_MS,
                current = {
                    LocationServices.getFusedLocationProviderClient(context)
                        .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellation.token)
                        .await()
                },
                fallback = { getLastKnownLocation(context) },
            )
        } finally {
            cancellation.cancel()
        }
    }

    internal suspend fun <T : Any> currentOrFallback(
        timeoutMs: Long,
        current: suspend () -> T?,
        fallback: () -> T?,
    ): T? {
        val fix =
            try {
                withTimeoutOrNull(timeoutMs) { current() }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        return fix ?: fallback()
    }

    @SuppressLint("MissingPermission")
    private fun getLastKnownLocation(context: Context): Location? {
        if (!hasLocationPermission(context)) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        return providers.mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull { it.time }
    }

    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    fun hasBackgroundLocationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return hasLocationPermission(context)
        }
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun canUseGeofencing(context: Context): Boolean = hasLocationPermission(context)

    fun canCheckLocationInBackground(context: Context): Boolean =
        hasLocationPermission(context) && hasBackgroundLocationPermission(context)
}

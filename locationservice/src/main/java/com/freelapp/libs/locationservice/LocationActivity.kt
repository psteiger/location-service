package com.freelapp.libs.locationservice

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.IBinder
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.actor

/**
 * Service connection vars and binding code common to all classes is in this abstract class.
 */
abstract class LocationActivity : AppCompatActivity() {

    companion object {
        const val HAS_LOCATION_PERMISSION_CODE = 11666
        val LOCATION_PERMISSION = arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
    }

    internal var locationService: LocationService? = null
        private set

    internal var currentLocation: Location? = null

    internal var bound: Boolean = false
        private set

    private val locationServiceConn: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            locationService = (service as LocationService.LocalBinder).service
            bound = true
            onLocationServiceConnected() // overridden and implemented by children activities
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            locationService = null
            bound = false
            onLocationServiceDisconnected()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!bound) askForPermissionAndBind()
    }

    override fun onDestroy() {
        super.onDestroy()

        if (bound) {
            unbindService(locationServiceConn)
            bound = false
        }
    }

    private fun bind() = bindService(
        Intent(this, LocationService::class.java),
        locationServiceConn,
        Context.BIND_AUTO_CREATE
    )

    private fun askForPermissionAndBind() {
        if (!bound) {
            if (ContextCompat.checkSelfPermission(this,
                    LOCATION_PERMISSION.first()) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, LOCATION_PERMISSION, HAS_LOCATION_PERMISSION_CODE)
            } else {
                bind()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>,
                                            grantResults: IntArray) {
        when (requestCode) {
            HAS_LOCATION_PERMISSION_CODE -> {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                    bind()
                } else if (ActivityCompat.shouldShowRequestPermissionRationale(this, LOCATION_PERMISSION.first())) {
                    Snackbar
                        .make(
                            findViewById(android.R.id.content),
                            getString(R.string.need_location_permission),
                            Snackbar.LENGTH_INDEFINITE
                        )
                        .show()

                    if (ContextCompat.checkSelfPermission(this, LOCATION_PERMISSION.first()) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(this, LOCATION_PERMISSION, HAS_LOCATION_PERMISSION_CODE)
                    }
                }
            }
        }
    }

    // override this !
    internal open fun onLocationServiceConnected() = Unit
    internal open fun onLocationServiceDisconnected() = Unit

    sealed class LocationServiceMsg {
        class AddLocationListener(val listener: ILocationListener) : LocationServiceMsg()
        class RemoveLocationListener(val listener: ILocationListener) : LocationServiceMsg()
    }

    @ObsoleteCoroutinesApi
    private val locationServiceActor = GlobalScope.actor<LocationServiceMsg>(Dispatchers.Main) {
        for (msg in channel) {
            while (!bound) delay(500)

            when (msg) {
                is LocationServiceMsg.AddLocationListener -> locationService?.addLocationListener(msg.listener)
                is LocationServiceMsg.RemoveLocationListener -> locationService?.removeLocationListener(msg.listener)
            }
        }
    }
    /* update whoever registers about location changes */
    @ObsoleteCoroutinesApi
    fun addLocationListener(listener: ILocationListener) = GlobalScope.launch {
        locationServiceActor.send(LocationServiceMsg.AddLocationListener(listener))
    }

    @ObsoleteCoroutinesApi
    fun removeLocationListener(listener: ILocationListener) = GlobalScope.launch {
        locationServiceActor.send(LocationServiceMsg.RemoveLocationListener(listener))
    }
}

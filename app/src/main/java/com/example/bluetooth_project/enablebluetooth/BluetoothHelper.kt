package com.example.bluetooth_project.enablebluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat

class BluetoothHelper(private val context: Context, private val listener: BluetoothHelperListener) {

    private val mBluetoothAdapter by lazy {

        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter

        bluetoothManager.adapter?.let {
            return@lazy it
        } ?: run {
            throw RuntimeException(
                "Bluetooth is not supported on this hardware platform. " +
                        "Make sure you try it from the real device\n " +
                        "You could more information from here:\n" +
                        "https://developer.android.com/reference/android/bluetooth/BluetoothAdapter"
            )
        }
    }

    private var isRequiredPermission = false

    private var isEnabled = mBluetoothAdapter.isEnabled

    private val mBluetoothStateChangeReceiver by lazy {
        object : BluetoothStateChangeReceiver() {
            /*  override fun onStartDiscovering() {
                  isDiscovering = true
                  listener.onStartDiscovery()
              }

              override fun onFinishDiscovering() {
                  isDiscovering = false
                  listener.onFinishDiscovery()
              }
  */
            override fun onEnabledBluetooth() {
                isEnabled = true
                listener.onEnabledBluetooth()
            }

            override fun onDisabledBluetooth() {
                isEnabled = false
                listener.onDisabledBluetooh()
            }
        }
    }

    fun isBluetoothEnabled() = isEnabled

    fun enableBluetooth() {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        if (!isEnabled)
            mBluetoothAdapter.enable()
    }

    fun disableBluetooth() {
        if (isEnabled) mBluetoothAdapter.disable()
    }

    fun registerBluetoothStateChanged() {
        val intentFilter = IntentFilter()
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        context.registerReceiver(mBluetoothStateChangeReceiver, intentFilter)
    }

    fun unregisterBluetoothStateChanged() {
        context.unregisterReceiver(mBluetoothStateChangeReceiver)
    }

    fun setPermissionRequired(isRequired: Boolean): BluetoothHelper {
        this.isRequiredPermission = isRequired
        return this
    }

    fun create(): BluetoothHelper {
        return BluetoothHelper(context, listener)
    }
}
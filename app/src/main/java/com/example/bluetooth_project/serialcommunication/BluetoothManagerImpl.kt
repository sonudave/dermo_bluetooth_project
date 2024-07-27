package com.example.bluetooth_project.serialcommunication

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.util.Log
import io.reactivex.Single
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.*

/**
 * Implementation of BluetoothManager, package-private
 */
internal class BluetoothManagerImpl(private val adapter: BluetoothAdapter) : BluetoothManager {
    private val devices: MutableMap<String, BluetoothSerialDeviceImpl> = mutableMapOf()

    override val pairedDevices: Collection<BluetoothDevice>
        get() = adapter.bondedDevices

    override fun openSerialDevice(mac: String): Single<BluetoothSerialDevice> {
        Log.e("openSerialDevice", mac.toString())

        return openSerialDevice(mac, StandardCharsets.UTF_8)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun openSerialDevice(mac: String, charset: Charset): Single<BluetoothSerialDevice> {
        return if (devices.containsKey(mac)) {
            Log.e("devices.containsKey", mac)
            Single.just(devices[mac]!!)
        } else {
            Single.fromCallable {
                try {
                    Log.e("fromCallable", mac)
                    val device = adapter.getRemoteDevice(mac)
                  /*  if (ActivityCompat.checkSelfPermission(
                            BluetoothApplication.bluetoothApplication,
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
                    }*/
                    Log.e("device devices", device.uuids[0].toString() +"  "+devices.size)
                    val SPP_UUID: UUID = UUID.fromString(device.uuids[0].toString())
                    val socket = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
                    Log.e("socket", mac+"  "+socket.isConnected)

                    adapter.cancelDiscovery()
                    socket.connect()
                    Log.e("devices  ", "   "+socket.isConnected)

                    val serialDevice = BluetoothSerialDeviceImpl(mac, socket, charset)
                    devices[mac] = serialDevice
                    return@fromCallable serialDevice
                } catch (e: Exception) {
                    Log.e("Exception  ",""+e.toString())
                    throw BluetoothConnectException(e)
                }
            }
        }
    }

    override fun closeDevice(mac: String) {
        devices.remove(mac)?.close()
    }

    override fun closeDevice(device: BluetoothSerialDevice) {
        closeDevice(device.mac)
    }

    override fun closeDevice(deviceInterface: SimpleBluetoothDeviceInterface) {
        closeDevice(deviceInterface.device.mac)
    }

    override fun close() {
        for (device in devices.values) {
            try {
                device.close()
            } catch (ignored: Throwable) {
            }
        }
        devices.clear()
    }

    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")
    }
}

package com.example.bluetooth_project.ui

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Handler
import android.text.TextUtils
import android.util.Log
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.app.ActivityCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.bluetooth_project.BluetoothApplication
import com.example.bluetooth_project.R
import com.example.bluetooth_project.serialcommunication.BluetoothManager
import com.example.bluetooth_project.serialcommunication.SimpleBluetoothDeviceInterface
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import javax.inject.Inject

@ExperimentalCoroutinesApi
@HiltViewModel
class MainViewModel @Inject constructor() : ViewModel() {

    // Our BluetoothManager!
    private var bluetoothManager: BluetoothManager? = null
    private var deviceName: String? = null
    private var mac: String? = null
    private val connectionStatusData = MutableLiveData<ConnectionStatus>()

    // The paired devices list tha the activity sees
    private val pairedDeviceList = MutableLiveData<Collection<BluetoothDevice>>()
    private var deviceInterface: SimpleBluetoothDeviceInterface? = null
    private var messages = java.lang.StringBuilder()
    private val messageData = MutableLiveData<String>()
    private val messagesData = MutableLiveData<String>()
    private val deviceNameData = MutableLiveData<String>()
    val _sendConnectivity = MutableLiveData<Boolean>()

    val _pairedDeviceList: LiveData<Collection<BluetoothDevice>>
        get() = pairedDeviceList
    val sendConnectivity: LiveData<Boolean>
        get() = _sendConnectivity


    // A variable to help us not setup twice
    private var viewModelSetup = false

    fun setupViewModel(): Boolean {
        // Check we haven't already been called
        if (!viewModelSetup) {
            viewModelSetup = true

            // Setup our BluetoothManager
            bluetoothManager = BluetoothManager.instance
            if (bluetoothManager == null) {
                // Bluetooth unavailable on this device :( tell the user
                Toast.makeText(
                    BluetoothApplication.bluetoothApplication,
                    R.string.no_bluetooth,
                    Toast.LENGTH_LONG
                ).show()
                // Tell the activity there was an error and to close
                return false
            }
        }
        // If we got this far, nothing went wrong, so return true
        return true
    }

    // Called by the activity to request that we refresh the list of paired devices
    fun refreshPairedDevices() {
        pairedDeviceList.value = bluetoothManager?.pairedDevices
        //   pairedDeviceList.postValue(bluetoothManager?.pairedDevices)
    }

    // Called when the activity finishes - clear up after ourselves.
    override fun onCleared() {
        if (bluetoothManager != null) bluetoothManager!!.close()
    }

    // Getter method for the activity to use.
    fun getPairedDeviceList(): LiveData<Collection<BluetoothDevice>> {
        return _pairedDeviceList
    }

    private var connectionAttemptedOrMade = false

    private val compositeDisposable = CompositeDisposable()

    fun connect(bluetoothDevice: BluetoothDevice) {
        Log.e("connect", bluetoothDevice?.name!!)

        if (ActivityCompat.checkSelfPermission(
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
        }
        this.deviceName = bluetoothDevice.name
        this.mac = bluetoothDevice.address

        // Tell the activity the device name so it can set the title
        deviceNameData.value = deviceName.toString()
        Log.e("deviceNameData", deviceNameData.value.toString())

        // Check we are not already connecting or connected
        if (!connectionAttemptedOrMade) {
            Log.e("connectionAttemptedOrMade", mac.toString())
            connectionAttemptedOrMade = true
            // Tell the activity that we are connecting.
            // Tell the activity that we are connecting.
            connectionStatusData.postValue(ConnectionStatus.CONNECTING)
            // Connect asynchronously
            compositeDisposable.add(
                bluetoothManager!!.openSerialDevice(mac.toString())
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe({ device -> onConnected(device.toSimpleDeviceInterface()) }) { t ->
                        toast(R.string.connection_failed)
                        connectionAttemptedOrMade = false
                        connectionStatusData.postValue(ConnectionStatus.DISCONNECTED)
                    })
            // Remember that we made a connection attempt.
            // Remember that we made a connection attempt.

            // Connect asynchronously
/*
            compositeDisposable.add(
                bluetoothManager!!.openSerialDevice(mac!!)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())//device.toSimpleDeviceInterface()
                    .subscribe({
                        onConnected(object : SimpleBluetoothDeviceInterface {
                            override val device: BluetoothSerialDevice
                                get() =bluetoothDevice


                            override fun sendMessage(message: String) {

                            }

                            override fun setListeners(
                                messageReceivedListener: SimpleBluetoothDeviceInterface.OnMessageReceivedListener?,
                                messageSentListener: SimpleBluetoothDeviceInterface.OnMessageSentListener?,
                                errorListener: SimpleBluetoothDeviceInterface.OnErrorListener?
                            ) {
                            }

                            override fun setMessageReceivedListener(listener: SimpleBluetoothDeviceInterface.OnMessageReceivedListener?) {
                            }

                            override fun setMessageSentListener(listener: SimpleBluetoothDeviceInterface.OnMessageSentListener?) {
                            }

                            override fun setErrorListener(listener: SimpleBluetoothDeviceInterface.OnErrorListener?) {
                            }
                        })
                    }) { t ->
                        toast(R.string.connection_failed)
                        connectionAttemptedOrMade = false
                        connectionStatusData.postValue(ConnectionStatus.DISCONNECTED)
                    })
*/
            // Remember that we made a connection attempt.
            connectionAttemptedOrMade = true
            // Tell the activity that we are connecting.
            connectionStatusData.postValue(ConnectionStatus.CONNECTING)
        }
    }

    // Called once the library connects a bluetooth device
    private fun onConnected(deviceInterface: SimpleBluetoothDeviceInterface) {
        this.deviceInterface = deviceInterface
        if (deviceInterface != null) {
            Log.e("onConnected", "deviceName   " + deviceInterface.device)

            // We have a device! Tell the activity we are connected.
            connectionStatusData.postValue(ConnectionStatus.CONNECTED)
            Log.e("connectionStatusData", "postValue   " + deviceInterface.device)

            // Setup the listeners for the interface

            deviceInterface.setListeners(object :
                SimpleBluetoothDeviceInterface.OnMessageReceivedListener {
                override fun onMessageReceived(message: String) {
                    messages.append(deviceName).append(": ").append(message).append('\n')
                    messagesData.postValue(messages.toString())
                }
            }, object : SimpleBluetoothDeviceInterface.OnMessageSentListener {
                override fun onMessageSent(message: String) {
                    messages.append(BluetoothApplication.bluetoothApplication.getString(R.string.you_sent))
                        .append(": ")
                        .append(message).append('\n')
                    messagesData.postValue(messages.toString())
                    // Reset the message box
                    messageData.postValue("")
                }
            }, object : SimpleBluetoothDeviceInterface.OnErrorListener {
                override fun onError(error: Throwable) {

                }
            })

            // Tell the user we are connected.
            //toast(R.string.connected)
            sendConnectionMessage("30")
            sendContineousConnection()
            // Reset the conversation
            messages = StringBuilder()
            messageData.postValue(messages.toString())
        } else {
            // deviceInterface was null, so the connection failed
            toast(R.string.connection_failed)
            connectionStatusData.postValue(ConnectionStatus.DISCONNECTED)
        }
    }

    init {
        _sendConnectivity.value = true
    }

    val run = Runnable {
      //  sendConnectionMessage("30")
        sendContineousConnection()
        //  Toast.makeText(this@MainActivity, "clicked", Toast.LENGTH_SHORT).show()
        // Your code to run on long click
    }

    val handel: Handler = Handler()

    // Helper method to create toast messages.
    private fun toast(@StringRes messageResource: Int) {
    }

    fun sendContineousConnection() {
        if (_sendConnectivity.value == true) {
            handel.postDelayed(run, 5000/* OR the amount of time you want */)
       }
    }

    fun sendConnectionMessage(message: String?) {
        if (deviceInterface != null && !TextUtils.isEmpty(message)) {
            deviceInterface!!.sendMessage(message!!)
        }
    }

    fun sendMessage(message: String?) {
        // Check we have a connected device and the message is not empty, then send the message
        if (deviceInterface != null && !TextUtils.isEmpty(message)) {
            deviceInterface!!.sendMessage(message!!)
        }
    }

    internal enum class ConnectionStatus {
        DISCONNECTED, CONNECTING, CONNECTED
    }

}
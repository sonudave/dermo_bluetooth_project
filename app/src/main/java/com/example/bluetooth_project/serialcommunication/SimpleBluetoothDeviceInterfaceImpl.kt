package com.example.bluetooth_project.serialcommunication

import android.util.Log
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers

/**
 * Implementation of SimpleBluetoothDeviceInterface, package-private
 */
internal class SimpleBluetoothDeviceInterfaceImpl(override val device: BluetoothSerialDeviceImpl) :
    SimpleBluetoothDeviceInterface {
    private val compositeDisposable = CompositeDisposable()

    private var messageReceivedListener: SimpleBluetoothDeviceInterface.OnMessageReceivedListener? =
        null
    private var messageSentListener: SimpleBluetoothDeviceInterface.OnMessageSentListener? = null
    private var errorListener: SimpleBluetoothDeviceInterface.OnErrorListener? = null

    init {
        compositeDisposable.add(
            device.openMessageStream()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    {
                        Log.e("openMessageStream", "" + it)

                        messageReceivedListener?.onMessageReceived(it)
                    },
                    {
                        Log.e("errorListener", "" + it)

                        errorListener?.onError(it)
                    })
        )
    }

    override fun sendMessage(message: String) {
        device.checkNotClosed()
        compositeDisposable.add(
            device.send(message)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    {
                        messageSentListener?.onMessageSent(message)
                        Log.e("messageSentListener", "" + message)
                        checkIfMessageReceived()
                    },
                    {
                        errorListener?.onError(it)
                        Log.e("senderrorListener", "" + it)
                    })
        )
    }

    private fun checkIfMessageReceived() {
        compositeDisposable.add(
            device.openMessageStream()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    {
                        messageReceivedListener?.onMessageReceived(it)
                        Log.e("openMessageStream", "" + it)
                    },
                    {
                        errorListener?.onError(it)
                        Log.e("errorListener", "" + it)
                    })
        )
    }


    override fun setListeners(
        messageReceivedListener: SimpleBluetoothDeviceInterface.OnMessageReceivedListener?,
        messageSentListener: SimpleBluetoothDeviceInterface.OnMessageSentListener?,
        errorListener: SimpleBluetoothDeviceInterface.OnErrorListener?
    ) {
        this.messageReceivedListener = messageReceivedListener
        this.messageSentListener = messageSentListener
        this.errorListener = errorListener
    }

    override fun setMessageReceivedListener(listener: SimpleBluetoothDeviceInterface.OnMessageReceivedListener?) {
        messageReceivedListener = listener
    }

    override fun receivedMessage(message: String) {
        Log.e("receivedMessage", "receivedMessage")
    }


    override fun setMessageSentListener(listener: SimpleBluetoothDeviceInterface.OnMessageSentListener?) {
        messageSentListener = listener
    }

    override fun setErrorListener(listener: SimpleBluetoothDeviceInterface.OnErrorListener?) {
        errorListener = listener
    }

    fun close() {
        compositeDisposable.dispose()
    }
}

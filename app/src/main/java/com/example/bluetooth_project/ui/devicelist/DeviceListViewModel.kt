package com.example.bluetooth_project.ui.devicelist

import android.bluetooth.BluetoothDevice
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import com.example.bluetooth_project.utils.SingleLiveEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import javax.inject.Inject

@ExperimentalCoroutinesApi
@HiltViewModel
class DeviceListViewModel @Inject constructor() : ViewModel() {

    private val _showKeplerScreen = SingleLiveEvent<String>()

    val showKeplerScreen: LiveData<String>
        get() = _showKeplerScreen

    fun onItemClickListener(items: BluetoothDevice) {
        _showKeplerScreen.value=items.address
    }
}
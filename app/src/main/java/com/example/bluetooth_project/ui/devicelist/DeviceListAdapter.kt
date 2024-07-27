package com.example.bluetooth_project.ui.devicelist

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.app.ActivityCompat
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.bluetooth_project.R
import com.example.bluetooth_project.databinding.DeviceListItemBinding
import kotlinx.coroutines.ExperimentalCoroutinesApi

@ExperimentalCoroutinesApi
class DeviceListAdapter constructor(
    private val context: Context,
    private val viewModel: DeviceListViewModel
) : RecyclerView.Adapter<DeviceListAdapter.DeviceListHolder>() {

    private val list = ArrayList<BluetoothDevice>()


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceListHolder =
        DeviceListHolder(
            DataBindingUtil.inflate(
                LayoutInflater.from(parent.context),
                R.layout.device_list_item,
                parent,
                false
            )
        )

    override fun onBindViewHolder(holder: DeviceListHolder, position: Int) {
        holder.bind(list[position])
    }

    override fun getItemCount(): Int = list.size

    fun submitList(list: ArrayList<BluetoothDevice>) {
        this.list.clear()
        this.list.addAll(list)
        notifyDataSetChanged()
    }

    inner class DeviceListHolder(
        private val deviceListItemBinding: DeviceListItemBinding
    ) : RecyclerView.ViewHolder(deviceListItemBinding.root) {
        fun bind(items: BluetoothDevice) = with(items) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {

                return
            }
            Log.e("bind bind ","  "+items.name +"  "+items.address)
            deviceListItemBinding.text1.text = items.name
            deviceListItemBinding.text2.text = items.address
            deviceListItemBinding.executePendingBindings()
            deviceListItemBinding.deviceItem.setOnClickListener {
                viewModel.onItemClickListener(items)
            }
        }
    }


}
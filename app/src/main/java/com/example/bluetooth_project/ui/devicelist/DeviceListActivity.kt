package com.example.bluetooth_project.ui.devicelist

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.bluetooth_project.R
import com.example.bluetooth_project.databinding.ActivityDeviceListBinding
import com.example.bluetooth_project.sendreceivemessage.BluetoothUtil
import com.example.bluetooth_project.ui.MainActivity
import com.example.bluetooth_project.ui.base.BaseActivity
import com.example.bluetooth_project.utils.hideStatusBar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.ExperimentalCoroutinesApi

@ExperimentalCoroutinesApi
@AndroidEntryPoint
class DeviceListActivity : BaseActivity<DeviceListViewModel>() {

    private lateinit var binding: ActivityDeviceListBinding
    override val mViewModel: DeviceListViewModel
        get() = ViewModelProvider(this)[DeviceListViewModel::class.java]

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothManager: BluetoothManager? = null
    private val listItems = ArrayList<BluetoothDevice>()
    private var permissionMissing = false
    private var deviceListAdapter: DeviceListAdapter? = null

    private var requestBluetoothPermissionLauncherForRefresh =
        registerForActivityResult<String, Boolean>(
            ActivityResultContracts.RequestPermission()
        ) { granted: Boolean? ->
            if (granted != null) {
                BluetoothUtil.onPermissionsResult(
                    this,
                    granted, BluetoothUtil.PermissionGrantedCallback { refresh() }
                )
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeviceListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        hideStatusBar(this)
        setUpRecyclerView()
        if (this.packageManager
                .hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
        ) {
            bluetoothManager = this.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            bluetoothAdapter = bluetoothManager?.adapter
        }
        if (BluetoothUtil.hasPermissions(
                this,
                requestBluetoothPermissionLauncherForRefresh
            )
        ) {
            refresh()
        }
        observeViewModel()
    }

    private fun observeViewModel() {
        mViewModel.showKeplerScreen.observe(this) {
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("Address", it)
            startActivity(intent)
            finish()
        }
    }

    private fun setUpRecyclerView() {
        deviceListAdapter = DeviceListAdapter(this, mViewModel)
        binding.deviceListRecyclerView.apply {
            layoutManager = LinearLayoutManager(
                this@DeviceListActivity,
                LinearLayoutManager.VERTICAL,
                false
            )
            setHasFixedSize(true)
            adapter = deviceListAdapter
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        listItems.clear()
        if (bluetoothAdapter != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                permissionMissing =
                    this.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
            }
            if (!permissionMissing) {
                if (bluetoothAdapter?.isEnabled == true) {
                    val item = bluetoothAdapter?.bondedDevices
                        ?.filter { it.type != BluetoothDevice.DEVICE_TYPE_LE }
                        ?.sortedWith(compareBy { it.name })
                    if (item != null) {
                        listItems.addAll(item)
                        Log.e("refresh refresh", " " + listItems.size)
                        deviceListAdapter?.submitList(listItems)

                    }
                } else {
                    val bluetoothIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    registerForResult.launch(bluetoothIntent)
                }
            }
        }
    }

    private val registerForResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            //  getAndConnectToBluetooth()
            refresh()
            Toast.makeText(
                this@DeviceListActivity,
                getString(R.string.bluetooth_enabled),
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
package com.example.bluetooth_project.ui


import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.MotionEvent
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.example.bluetooth_project.BluetoothApplication
import com.example.bluetooth_project.R
import com.example.bluetooth_project.databinding.ActivityMainBinding
import com.example.bluetooth_project.sendreceivemessage.SerialListener
import com.example.bluetooth_project.sendreceivemessage.SerialService
import com.example.bluetooth_project.sendreceivemessage.SerialSocket
import com.example.bluetooth_project.sendreceivemessage.TextUtil
import com.example.bluetooth_project.ui.base.BaseActivity
import com.example.bluetooth_project.utils.hideStatusBar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.util.*

@ExperimentalCoroutinesApi
@AndroidEntryPoint
class MainActivity : BaseActivity<MainViewModel>(), ServiceConnection, SerialListener {

    private enum class Connected {
        False, Pending, True
    }

    override val mViewModel: MainViewModel
        get() = ViewModelProvider(this)[MainViewModel::class.java]

    private lateinit var binding: ActivityMainBinding
    private var isClickable = false
    private var deviceAddress: String? = null
    private var service: SerialService? = null
    private var initialStart = true
    private val receiveText: TextView? = null
    private var connected: Connected =
        Connected.False
    private val hexEnabled = false
    private var hexWatcher: TextUtil.HexWatcher? = null
    private val newline = TextUtil.newline_crlf
    private var pendingNewline = false
    private var sendConnectionReq = false

    //9
    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        hideStatusBar(this)
        this.startService(
            Intent(
                BluetoothApplication.bluetoothApplication.applicationContext,
                SerialService::class.java
            )
        )
        hexWatcher = TextUtil.HexWatcher(binding.chairLockTextView)
        hexWatcher?.enable(hexEnabled)
        deviceAddress = intent.getStringExtra("Address")
        Log.e(" deviceAddress deviceAddress", " " + intent.getStringExtra("Address"))
        checkAndEnableBluetooth()
        // This method return false if there is an error, so if it does, we should close.
        if (!mViewModel.setupViewModel()) {
            finish()
            return
        }
        // Start observing the data sent to us by the ViewModel
        setUpOnClickListener()
        sendConnectionReq = true
        observeConnectivity()
        setConnectionRequestTrue()
    }


    override fun onStart() {
        super.onStart()
        this.bindService(
            Intent(this, SerialService::class.java),
            this,
            BIND_AUTO_CREATE
        )
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        this.bindService(
            Intent(this, SerialService::class.java),
            this,
            BIND_AUTO_CREATE
        )
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun checkAndEnableBluetooth() {
        getAndConnectToBluetooth()
        /*  val bluetoothManager = this.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
          val adapter = bluetoothManager.adapter
          if (ActivityCompat.checkSelfPermission(
                  this,
                  Manifest.permission.BLUETOOTH_CONNECT
              ) != PackageManager.PERMISSION_GRANTED
          ) {
              permissionsResultCallback.launch(
                  arrayOf(
                      Manifest.permission.BLUETOOTH_CONNECT,
                      Manifest.permission.BLUETOOTH_SCAN,
                  )
              )

          } else if (!adapter.isEnabled) {
              val bluetoothIntent = Intent(ACTION_REQUEST_ENABLE)
              registerForResult.launch(bluetoothIntent)
          } else {
              getAndConnectToBluetooth()
          }*/

    }

    @RequiresApi(Build.VERSION_CODES.S)
    private val permissionsResultCallback = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        when {
            permissions[Manifest.permission.BLUETOOTH_CONNECT] == true &&
                    permissions[Manifest.permission.BLUETOOTH_SCAN] == true -> {
                checkAndEnableBluetooth()
            }
            permissions[Manifest.permission.BLUETOOTH_SCAN] == true -> {

            }
            else -> {}
        }
    }

    private fun getAndConnectToBluetooth() {
        connect()
/*
        mViewModel.getPairedDeviceList()
            .observe(this@MainActivity) { deviceList: Collection<BluetoothDevice?>? ->

                Log.e("getPairedDeviceList", "" + deviceList?.size)
                val device = deviceList?.toList()
                for (i in 0..device?.size?.minus(1)!!) {
                    val bluetoothDevice = device[i]
                    if (ActivityCompat.checkSelfPermission(
                            this,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                        && ActivityCompat.checkSelfPermission(
                            this,
                            Manifest.permission.BLUETOOTH_SCAN
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {

                        return@observe
                    }
                    if (bluetoothDevice?.name == "Two M") {
                        Log.e("bluetoothDevice", bluetoothDevice?.name!!)
                        //    mViewModel.connect(bluetoothDevice)
                        deviceAddress = bluetoothDevice?.address
                        Log.e("deviceAddress ", " " + deviceAddress)
                        connect()
                    }
                }
            }
*/

    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onResume() {
        super.onResume()
        if (initialStart && service != null) {
            Log.e("onResume", "onResume")
            initialStart = false
            this.runOnUiThread(Runnable { connect() })
        }
       // mViewModel.refreshPairedDevices()
       // checkAndEnableBluetooth()
    }

    private fun connect() {
        try {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            val device = bluetoothAdapter.getRemoteDevice(deviceAddress)
            val socket =
                SerialSocket(BluetoothApplication.bluetoothApplication.applicationContext, device)
            service!!.connect(socket)
        } catch (e: java.lang.Exception) {
            onSerialConnectError(e)
        }
    }

    private fun disconnect() {
        service!!.disconnect()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private val registerForResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            //  getAndConnectToBluetooth()
            checkAndEnableBluetooth()
            Toast.makeText(
                this@MainActivity,
                getString(R.string.bluetooth_enabled),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private val handlerConnectionRequest: Handler = Handler()


    private fun setConnectionRequestTrue() {
        if (sendConnectionReq)
            handlerConnectionRequest.postDelayed(
                runConnectionReq,
                1000/* OR the amount of time you want */
            )
    }

    private val runConnectionReq = Runnable {
        mViewModel._sendConnectivity.value = true
        setConnectionRequestTrue()
    }

    private val runChairBack = Runnable {
        send("17")
        chairBack()
    }

    private fun chairBack() {
        handlerChairBack.postDelayed(runChairBack, 85/* OR the amount of time you want */)
    }

    private val handlerChairBack: Handler = Handler()

    private val runChairFront = Runnable {
        send("19")
        chairFront()
    }

    private fun chairFront() {
        handlerChairFront.postDelayed(runChairFront, 85/* OR the amount of time you want */)
    }

    private val handlerChairFront: Handler = Handler()

    private val runChairUp = Runnable {
        // mViewModel.sendMessage("16")
        send("09")
        chairUp()
    }

    private fun chairUp() {
        handlerChairUp.postDelayed(runChairUp, 85/* OR the amount of time you want */)
    }

    private val handlerChairUp: Handler = Handler()

    private val runChairDown = Runnable {
        //     mViewModel.sendMessage("18")
        send("18")
        chairDown()
    }

    private fun chairDown() {
        handlerChairDown.postDelayed(runChairDown, 85/* OR the amount of time you want */)
    }

    private val handlerChairDown: Handler = Handler()

    private val runArmBack = Runnable {
        //  mViewModel.sendMessage("21")
        send("21")
        armBack()
    }

    private fun armBack() {
        handlerArmBack.postDelayed(runArmBack, 80/* OR the amount of time you want */);
    }

    private val handlerArmBack: Handler = Handler()

    private val runArmFront = Runnable {
        //   mViewModel.sendMessage("23")
        send("23")
        armFront()
    }

    private fun armFront() {
        handlerArmFront.postDelayed(runArmFront, 85/* OR the amount of time you want */);
    }

    private val handlerArmFront: Handler = Handler()


    private val runArmUp = Runnable {
        // mViewModel.sendMessage("20")
        send("20")
        armUp()
    }

    private fun armUp() {
        handlerArmUp.postDelayed(runArmUp, 80/* OR the amount of time you want */)
    }

    private val handlerArmUp: Handler = Handler()


    private val runArmDown = Runnable {
        // mViewModel.sendMessage("22")
        send("22")
        armDown()
    }

    private fun armDown() {
        handlerArmDown.postDelayed(runArmDown, 85/* OR the amount of time you want */);
    }

    private val handlerArmDown: Handler = Handler()


    @SuppressLint("ClickableViewAccessibility")
    private fun setUpOnClickListener() {

        binding.chairLockImageView.setOnClickListener {
            //  mViewModel.sendMessage("01")
        }
        binding.chairLockImageView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    mViewModel._sendConnectivity.value = false
                    sendConnectionReq = false
                    send("01")
                    binding.chairLockImageView.background =
                        AppCompatResources.getDrawable(this, R.drawable.yellow_ring_bg)
                    binding.lampImageView.isEnabled = false
                    binding.auxOneImageView.isEnabled = false
                    binding.auxTwoImageView.isEnabled = false
                    binding.auxThreeImageView.isEnabled = false
                    binding.minusImageView.isEnabled = false
                    binding.instrumentOnImageView.isEnabled = false
                    binding.plusImageView.isEnabled = false
                    binding.chairZeroImageView.isEnabled = false
                    binding.armZero.isEnabled = false

                    binding.chairFrontImageView.isEnabled = false
                    binding.chairBackImageView.isEnabled = false
                    binding.chairUpImageView.isEnabled = false
                    binding.chairDownImageView.isEnabled = false
                    binding.armUpImageView.isEnabled = false
                    binding.armDownImageView.isEnabled = false
                    binding.armFrontImageView.isEnabled = false
                    binding.armBackImageView.isEnabled = false

                }
                MotionEvent.ACTION_UP -> {
                    mViewModel._sendConnectivity.value = true
                    send("30")
                    sendConnectionReq = true
                    binding.chairLockImageView.setBackgroundColor(Color.TRANSPARENT)
                    binding.lampImageView.isEnabled = true
                    binding.auxOneImageView.isEnabled = true
                    binding.auxTwoImageView.isEnabled = true
                    binding.auxThreeImageView.isEnabled = true
                    binding.minusImageView.isEnabled = true
                    binding.instrumentOnImageView.isEnabled = true
                    binding.plusImageView.isEnabled = true
                    binding.chairZeroImageView.isEnabled = true
                    binding.armZero.isEnabled = true

                    binding.chairFrontImageView.isEnabled = true
                    binding.chairBackImageView.isEnabled = true
                    binding.chairUpImageView.isEnabled = true
                    binding.chairDownImageView.isEnabled = true
                    binding.armUpImageView.isEnabled = true
                    binding.armDownImageView.isEnabled = true
                    binding.armFrontImageView.isEnabled = true
                    binding.armBackImageView.isEnabled = true
                }
            }
            false
        }
        binding.lampImageView.setOnClickListener {
            //  mViewModel.sendMessage("02")
        }
        binding.lampImageView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    mViewModel._sendConnectivity.value = false
                    send("02")
                    sendConnectionReq = false
                    binding.lampImageView.background =
                        AppCompatResources.getDrawable(this, R.drawable.yellow_ring_bg)
                    binding.chairLockImageView.isEnabled = false
                    binding.auxOneImageView.isEnabled = false
                    binding.auxTwoImageView.isEnabled = false
                    binding.auxThreeImageView.isEnabled = false
                    binding.minusImageView.isEnabled = false
                    binding.instrumentOnImageView.isEnabled = false
                    binding.plusImageView.isEnabled = false
                    binding.chairZeroImageView.isEnabled = false
                    binding.armZero.isEnabled = false

                    binding.chairFrontImageView.isEnabled = true
                    binding.chairBackImageView.isEnabled = false
                    binding.chairUpImageView.isEnabled = false
                    binding.chairDownImageView.isEnabled = false
                    binding.armUpImageView.isEnabled = false
                    binding.armDownImageView.isEnabled = false
                    binding.armFrontImageView.isEnabled = false
                    binding.armBackImageView.isEnabled = false

                }
                MotionEvent.ACTION_UP -> {
                    mViewModel._sendConnectivity.value = true
                    send("30")
                    sendConnectionReq = true
                    binding.lampImageView.setBackgroundColor(Color.TRANSPARENT)
                    binding.chairLockImageView.isEnabled = true
                    binding.auxOneImageView.isEnabled = true
                    binding.auxTwoImageView.isEnabled = true
                    binding.auxThreeImageView.isEnabled = true
                    binding.minusImageView.isEnabled = true
                    binding.instrumentOnImageView.isEnabled = true
                    binding.plusImageView.isEnabled = true
                    binding.chairZeroImageView.isEnabled = true
                    binding.armZero.isEnabled = true

                    binding.chairFrontImageView.isEnabled = true
                    binding.chairBackImageView.isEnabled = true
                    binding.chairUpImageView.isEnabled = true
                    binding.chairDownImageView.isEnabled = true
                    binding.armUpImageView.isEnabled = true
                    binding.armDownImageView.isEnabled = true
                    binding.armFrontImageView.isEnabled = true
                    binding.armBackImageView.isEnabled = true
                }
            }
            false
        }


        binding.chairFrontImageView.setOnClickListener {
        }
        binding.chairFrontImageView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    mViewModel._sendConnectivity.value = false
                    send("19")
                    binding.chairFrontImageView.background =
                        AppCompatResources.getDrawable(this, R.drawable.yellow_ring_bg)
                    chairFront()
                    sendConnectionReq = false
                    binding.chairLockImageView.isEnabled = false
                    binding.lampImageView.isEnabled = false
                    binding.auxOneImageView.isEnabled = false
                    binding.auxTwoImageView.isEnabled = false
                    binding.auxThreeImageView.isEnabled = false
                    binding.minusImageView.isEnabled = false
                    binding.instrumentOnImageView.isEnabled = false
                    binding.plusImageView.isEnabled = false
                    binding.chairZeroImageView.isEnabled = false
                    binding.armZero.isEnabled = false

                    binding.chairBackImageView.isEnabled = false
                    binding.chairUpImageView.isEnabled = false
                    binding.chairDownImageView.isEnabled = false
                    binding.armUpImageView.isEnabled = false
                    binding.armDownImageView.isEnabled = false
                    binding.armFrontImageView.isEnabled = false
                    binding.armBackImageView.isEnabled = false
                }

                MotionEvent.ACTION_UP -> {
                    mViewModel._sendConnectivity.value = true
                    send("30")
                    binding.chairFrontImageView.setBackgroundColor(Color.TRANSPARENT)
                    handlerChairFront.removeCallbacks(runChairFront)
                    sendConnectionReq = true
                    setConnectionRequestTrue()
                    binding.chairLockImageView.isEnabled = true
                    binding.lampImageView.isEnabled = true
                    binding.auxOneImageView.isEnabled = true
                    binding.auxTwoImageView.isEnabled = true
                    binding.auxThreeImageView.isEnabled = true
                    binding.instrumentOnImageView.isEnabled = true
                    binding.plusImageView.isEnabled = true
                    binding.minusImageView.isEnabled = true
                    binding.chairZeroImageView.isEnabled = true
                    binding.armZero.isEnabled = true

                    binding.chairBackImageView.isEnabled = true
                    binding.chairUpImageView.isEnabled = true
                    binding.chairDownImageView.isEnabled = true
                    binding.armUpImageView.isEnabled = true
                    binding.armDownImageView.isEnabled = true
                    binding.armFrontImageView.isEnabled = true
                    binding.armBackImageView.isEnabled = true
                }

            }
            false
        }
        binding.chairBackImageView.setOnClickListener {
        }
        binding.chairBackImageView.setOnTouchListener { v, event ->

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    mViewModel._sendConnectivity.value = false
                    sendConnectionReq = false
                    send("17")
                    binding.chairBackImageView.background =
                        AppCompatResources.getDrawable(this, R.drawable.yellow_ring_bg)
                    chairBack()
                    binding.chairLockImageView.isEnabled = false
                    binding.lampImageView.isEnabled = false
                    binding.auxOneImageView.isEnabled = false
                    binding.auxTwoImageView.isEnabled = false
                    binding.auxThreeImageView.isEnabled = false
                    binding.minusImageView.isEnabled = false
                    binding.instrumentOnImageView.isEnabled = false
                    binding.plusImageView.isEnabled = false
                    binding.chairZeroImageView.isEnabled = false
                    binding.armZero.isEnabled = false


                    binding.chairFrontImageView.isEnabled = false
                    binding.chairUpImageView.isEnabled = false
                    binding.chairDownImageView.isEnabled = false
                    binding.armUpImageView.isEnabled = false
                    binding.armDownImageView.isEnabled = false
                    binding.armFrontImageView.isEnabled = false
                    binding.armBackImageView.isEnabled = false
                }
                MotionEvent.ACTION_UP -> {
                    mViewModel._sendConnectivity.value = true
                    send("30")
                    binding.chairBackImageView.setBackgroundColor(Color.TRANSPARENT)
                    handlerChairBack.removeCallbacks(runChairBack)
                    sendConnectionReq = true
                    setConnectionRequestTrue()
                    binding.chairLockImageView.isEnabled = true
                    binding.lampImageView.isEnabled = true
                    binding.auxOneImageView.isEnabled = true
                    binding.auxTwoImageView.isEnabled = true
                    binding.auxThreeImageView.isEnabled = true
                    binding.instrumentOnImageView.isEnabled = true
                    binding.plusImageView.isEnabled = true
                    binding.minusImageView.isEnabled = true
                    binding.chairZeroImageView.isEnabled = true
                    binding.armZero.isEnabled = true

                    binding.chairFrontImageView.isEnabled = true
                    binding.chairUpImageView.isEnabled = true
                    binding.chairDownImageView.isEnabled = true
                    binding.armUpImageView.isEnabled = true
                    binding.armDownImageView.isEnabled = true
                    binding.armFrontImageView.isEnabled = true
                    binding.armBackImageView.isEnabled = true
                }
            }
            false
        }
        binding.chairUpImageView.setOnClickListener {
        }
        binding.chairUpImageView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    mViewModel._sendConnectivity.value = false
                    sendConnectionReq = false
                    send("09")
                    binding.chairUpImageView.background =
                        AppCompatResources.getDrawable(this, R.drawable.yellow_ring_bg)
                    chairUp()
                    binding.chairLockImageView.isEnabled = false
                    binding.lampImageView.isEnabled = false
                    binding.auxOneImageView.isEnabled = false
                    binding.auxTwoImageView.isEnabled = false
                    binding.auxThreeImageView.isEnabled = false
                    binding.minusImageView.isEnabled = false
                    binding.instrumentOnImageView.isEnabled = false
                    binding.plusImageView.isEnabled = false
                    binding.chairZeroImageView.isEnabled = false
                    binding.armZero.isEnabled = false

                    binding.chairFrontImageView.isEnabled = false
                    binding.chairBackImageView.isEnabled = false
                    binding.chairDownImageView.isEnabled = false
                    binding.armUpImageView.isEnabled = false
                    binding.armDownImageView.isEnabled = false
                    binding.armFrontImageView.isEnabled = false
                    binding.armBackImageView.isEnabled = false
                }
                MotionEvent.ACTION_UP -> {
                    mViewModel._sendConnectivity.value = true
                    send("30")
                    binding.chairUpImageView.setBackgroundColor(Color.TRANSPARENT)
                    handlerChairUp.removeCallbacks(runChairUp)
                    sendConnectionReq = true
                    setConnectionRequestTrue()
                    binding.chairLockImageView.isEnabled = true
                    binding.lampImageView.isEnabled = true
                    binding.auxOneImageView.isEnabled = true
                    binding.auxTwoImageView.isEnabled = true
                    binding.auxThreeImageView.isEnabled = true
                    binding.instrumentOnImageView.isEnabled = true
                    binding.plusImageView.isEnabled = true
                    binding.minusImageView.isEnabled = true
                    binding.chairZeroImageView.isEnabled = true
                    binding.armZero.isEnabled = true

                    binding.chairFrontImageView.isEnabled = true
                    binding.chairBackImageView.isEnabled = true
                    binding.chairDownImageView.isEnabled = true
                    binding.armUpImageView.isEnabled = true
                    binding.armDownImageView.isEnabled = true
                    binding.armFrontImageView.isEnabled = true
                    binding.armBackImageView.isEnabled = true
                }
            }
            false
        }
        binding.chairDownImageView.setOnClickListener {
        }
        binding.chairDownImageView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    mViewModel._sendConnectivity.value = false
                    sendConnectionReq = false
                    send("18")
                    binding.chairDownImageView.background =
                        AppCompatResources.getDrawable(this, R.drawable.yellow_ring_bg)
                    chairDown()
                    binding.chairLockImageView.isEnabled = false
                    binding.lampImageView.isEnabled = false
                    binding.auxOneImageView.isEnabled = false
                    binding.auxTwoImageView.isEnabled = false
                    binding.auxThreeImageView.isEnabled = false
                    binding.minusImageView.isEnabled = false
                    binding.instrumentOnImageView.isEnabled = false
                    binding.plusImageView.isEnabled = false
                    binding.chairZeroImageView.isEnabled = false
                    binding.armZero.isEnabled = false

                    binding.chairFrontImageView.isEnabled = false
                    binding.chairBackImageView.isEnabled = false
                    binding.chairUpImageView.isEnabled = false
                    binding.armUpImageView.isEnabled = false
                    binding.armDownImageView.isEnabled = false
                    binding.armFrontImageView.isEnabled = false
                    binding.armBackImageView.isEnabled = false
                }
                MotionEvent.ACTION_UP -> {
                    mViewModel._sendConnectivity.value = true
                    send("30")
                    binding.chairDownImageView.setBackgroundColor(Color.TRANSPARENT)
                    handlerChairDown.removeCallbacks(runChairDown)
                    sendConnectionReq = true
                    setConnectionRequestTrue()
                    binding.chairLockImageView.isEnabled = true
                    binding.lampImageView.isEnabled = true
                    binding.auxOneImageView.isEnabled = true
                    binding.auxTwoImageView.isEnabled = true
                    binding.auxThreeImageView.isEnabled = true
                    binding.instrumentOnImageView.isEnabled = true
                    binding.plusImageView.isEnabled = true
                    binding.minusImageView.isEnabled = true
                    binding.chairZeroImageView.isEnabled = true
                    binding.armZero.isEnabled = true

                    binding.chairFrontImageView.isEnabled = true
                    binding.chairBackImageView.isEnabled = true
                    binding.chairUpImageView.isEnabled = true
                    binding.armUpImageView.isEnabled = true
                    binding.armDownImageView.isEnabled = true
                    binding.armFrontImageView.isEnabled = true
                    binding.armBackImageView.isEnabled = true
                }
            }
            false
        }

        binding.auxOneImageView.setOnClickListener {
            //    mViewModel.sendMessage("03")
        }
        binding.auxOneImageView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    mViewModel._sendConnectivity.value = false
                    //  mViewModel.sendMessage("03")
                    send("03")
                    sendConnectionReq = false
                    binding.auxOneImageView.background =
                        AppCompatResources.getDrawable(this, R.drawable.yellow_ring_bg)
                    binding.chairLockImageView.isEnabled = false
                    binding.lampImageView.isEnabled = false
                    binding.auxTwoImageView.isEnabled = false
                    binding.auxThreeImageView.isEnabled = false
                    binding.minusImageView.isEnabled = false
                    binding.instrumentOnImageView.isEnabled = false
                    binding.plusImageView.isEnabled = false
                    binding.chairZeroImageView.isEnabled = false
                    binding.armZero.isEnabled = false

                    binding.chairFrontImageView.isEnabled = false
                    binding.chairBackImageView.isEnabled = false
                    binding.chairUpImageView.isEnabled = false
                    binding.chairDownImageView.isEnabled = false
                    binding.armUpImageView.isEnabled = false
                    binding.armDownImageView.isEnabled = false
                    binding.armFrontImageView.isEnabled = false
                    binding.armBackImageView.isEnabled = false
                }
                MotionEvent.ACTION_UP -> {
                    mViewModel._sendConnectivity.value = true
                    sendConnectionReq = true
                    setConnectionRequestTrue()
                    binding.auxOneImageView.setBackgroundColor(Color.TRANSPARENT)
                    binding.chairLockImageView.isEnabled = true
                    binding.lampImageView.isEnabled = true
                    binding.auxTwoImageView.isEnabled = true
                    binding.auxThreeImageView.isEnabled = true
                    binding.minusImageView.isEnabled = true
                    binding.instrumentOnImageView.isEnabled = true
                    binding.plusImageView.isEnabled = true
                    binding.armZero.isEnabled = true
                    binding.chairZeroImageView.isEnabled = true

                    binding.chairFrontImageView.isEnabled = true
                    binding.chairBackImageView.isEnabled = true
                    binding.chairUpImageView.isEnabled = true
                    binding.chairDownImageView.isEnabled = true
                    binding.armUpImageView.isEnabled = true
                    binding.armDownImageView.isEnabled = true
                    binding.armFrontImageView.isEnabled = true
                    binding.armBackImageView.isEnabled = true
                }
            }
            false
        }
        binding.auxTwoImageView.setOnClickListener {
            // mViewModel.sendMessage("04")
        }
        binding.auxTwoImageView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    mViewModel._sendConnectivity.value = false
                    sendConnectionReq = false
                    send("04")
                    binding.auxTwoImageView.background =
                        AppCompatResources.getDrawable(this, R.drawable.yellow_ring_bg)
                    binding.chairLockImageView.isEnabled = false
                    binding.lampImageView.isEnabled = false
                    binding.auxOneImageView.isEnabled = false
                    binding.auxThreeImageView.isEnabled = false
                    binding.minusImageView.isEnabled = false
                    binding.instrumentOnImageView.isEnabled = false
                    binding.plusImageView.isEnabled = false
                    binding.chairZeroImageView.isEnabled = false
                    binding.armZero.isEnabled = false

                    binding.chairFrontImageView.isEnabled = false
                    binding.chairBackImageView.isEnabled = false
                    binding.chairUpImageView.isEnabled = false
                    binding.chairDownImageView.isEnabled = false
                    binding.armUpImageView.isEnabled = false
                    binding.armDownImageView.isEnabled = false
                    binding.armFrontImageView.isEnabled = false
                    binding.armBackImageView.isEnabled = false
                }
                MotionEvent.ACTION_UP -> {
                    mViewModel._sendConnectivity.value = true
                    sendConnectionReq = true
                    setConnectionRequestTrue()
                    binding.auxTwoImageView.setBackgroundColor(Color.TRANSPARENT)
                    binding.chairLockImageView.isEnabled = true
                    binding.lampImageView.isEnabled = true
                    binding.auxOneImageView.isEnabled = true
                    binding.auxThreeImageView.isEnabled = true
                    binding.minusImageView.isEnabled = true
                    binding.instrumentOnImageView.isEnabled = true
                    binding.plusImageView.isEnabled = true
                    binding.armZero.isEnabled = true
                    binding.chairZeroImageView.isEnabled = true

                    binding.chairFrontImageView.isEnabled = true
                    binding.chairBackImageView.isEnabled = true
                    binding.chairUpImageView.isEnabled = true
                    binding.chairDownImageView.isEnabled = true
                    binding.armUpImageView.isEnabled = true
                    binding.armDownImageView.isEnabled = true
                    binding.armFrontImageView.isEnabled = true
                    binding.armBackImageView.isEnabled = true
                }
            }
            false
        }
        binding.auxThreeImageView.setOnClickListener {
            //    mViewModel.sendMessage("05")
        }
        binding.auxThreeImageView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    mViewModel._sendConnectivity.value = false
                    sendConnectionReq = false
                    send("05")
                    binding.auxThreeImageView.background =
                        AppCompatResources.getDrawable(this, R.drawable.yellow_ring_bg)
                    binding.chairLockImageView.isEnabled = false
                    binding.lampImageView.isEnabled = false
                    binding.auxOneImageView.isEnabled = false
                    binding.auxTwoImageView.isEnabled = false
                    binding.minusImageView.isEnabled = false
                    binding.instrumentOnImageView.isEnabled = false
                    binding.plusImageView.isEnabled = false
                    binding.chairZeroImageView.isEnabled = false
                    binding.armZero.isEnabled = false

                    binding.chairFrontImageView.isEnabled = false
                    binding.chairBackImageView.isEnabled = false
                    binding.chairUpImageView.isEnabled = false
                    binding.chairDownImageView.isEnabled = false
                    binding.armUpImageView.isEnabled = false
                    binding.armDownImageView.isEnabled = false
                    binding.armFrontImageView.isEnabled = false
                    binding.armBackImageView.isEnabled = false
                }
                MotionEvent.ACTION_UP -> {
                    mViewModel._sendConnectivity.value = true
                    sendConnectionReq = true
                    setConnectionRequestTrue()
                    binding.auxThreeImageView.setBackgroundColor(Color.TRANSPARENT)
                    binding.chairLockImageView.isEnabled = true
                    binding.lampImageView.isEnabled = true
                    binding.auxOneImageView.isEnabled = true
                    binding.auxTwoImageView.isEnabled = true
                    binding.minusImageView.isEnabled = true
                    binding.instrumentOnImageView.isEnabled = true
                    binding.plusImageView.isEnabled = true
                    binding.armZero.isEnabled = true
                    binding.chairZeroImageView.isEnabled = true

                    binding.chairFrontImageView.isEnabled = true
                    binding.chairBackImageView.isEnabled = true
                    binding.chairUpImageView.isEnabled = true
                    binding.chairDownImageView.isEnabled = true
                    binding.armUpImageView.isEnabled = true
                    binding.armDownImageView.isEnabled = true
                    binding.armFrontImageView.isEnabled = true
                    binding.armBackImageView.isEnabled = true
                }
            }
            false
        }

        binding.instrumentOnImageView.setOnClickListener {
            //   mViewModel.sendMessage("06")
        }
        binding.instrumentOnImageView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    mViewModel._sendConnectivity.value = false
                    sendConnectionReq = false
                    send("06")
                    binding.instrumentOnImageView.background =
                        AppCompatResources.getDrawable(this, R.drawable.yellow_ring_bg)
                    binding.chairLockImageView.isEnabled = false
                    binding.lampImageView.isEnabled = false
                    binding.auxOneImageView.isEnabled = false
                    binding.auxTwoImageView.isEnabled = false
                    binding.auxThreeImageView.isEnabled = false
                    binding.minusImageView.isEnabled = false
                    binding.plusImageView.isEnabled = false
                    binding.chairZeroImageView.isEnabled = false
                    binding.armZero.isEnabled = false

                    binding.chairFrontImageView.isEnabled = false
                    binding.chairBackImageView.isEnabled = false
                    binding.chairUpImageView.isEnabled = false
                    binding.chairDownImageView.isEnabled = false
                    binding.armUpImageView.isEnabled = false
                    binding.armDownImageView.isEnabled = false
                    binding.armFrontImageView.isEnabled = false
                    binding.armBackImageView.isEnabled = false
                }
                MotionEvent.ACTION_UP -> {
                    mViewModel._sendConnectivity.value = true
                    sendConnectionReq = true
                    setConnectionRequestTrue()
                    binding.instrumentOnImageView.setBackgroundColor(Color.TRANSPARENT)
                    binding.chairLockImageView.isEnabled = true
                    binding.lampImageView.isEnabled = true
                    binding.auxOneImageView.isEnabled = true
                    binding.auxTwoImageView.isEnabled = true
                    binding.auxThreeImageView.isEnabled = true
                    binding.minusImageView.isEnabled = true
                    binding.plusImageView.isEnabled = true
                    binding.chairZeroImageView.isEnabled = true
                    binding.armZero.isEnabled = true

                    binding.chairFrontImageView.isEnabled = true
                    binding.chairBackImageView.isEnabled = true
                    binding.chairUpImageView.isEnabled = true
                    binding.chairDownImageView.isEnabled = true
                    binding.armUpImageView.isEnabled = true
                    binding.armDownImageView.isEnabled = true
                    binding.armFrontImageView.isEnabled = true
                    binding.armBackImageView.isEnabled = true
                }
            }
            false
        }
        binding.minusImageView.setOnClickListener {
            // mViewModel.sendMessage("56")
        }
        binding.minusImageView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    mViewModel._sendConnectivity.value = false
                    sendConnectionReq = false
                    send("56")
                    binding.minusImageView.background =
                        AppCompatResources.getDrawable(this, R.drawable.yellow_ring_bg)
                    binding.chairLockImageView.isEnabled = false
                    binding.lampImageView.isEnabled = false
                    binding.auxOneImageView.isEnabled = false
                    binding.auxTwoImageView.isEnabled = false
                    binding.auxThreeImageView.isEnabled = false
                    binding.instrumentOnImageView.isEnabled = false
                    binding.plusImageView.isEnabled = false
                    binding.chairZeroImageView.isEnabled = false
                    binding.armZero.isEnabled = false

                    binding.chairFrontImageView.isEnabled = false
                    binding.chairBackImageView.isEnabled = false
                    binding.chairUpImageView.isEnabled = false
                    binding.chairDownImageView.isEnabled = false
                    binding.armUpImageView.isEnabled = false
                    binding.armDownImageView.isEnabled = false
                    binding.armFrontImageView.isEnabled = false
                    binding.armBackImageView.isEnabled = false
                }
                MotionEvent.ACTION_UP -> {
                    mViewModel._sendConnectivity.value = true
                    sendConnectionReq = true
                    setConnectionRequestTrue()
                    binding.minusImageView.setBackgroundColor(Color.TRANSPARENT)
                    binding.chairLockImageView.isEnabled = true
                    binding.lampImageView.isEnabled = true
                    binding.auxOneImageView.isEnabled = true
                    binding.auxTwoImageView.isEnabled = true
                    binding.auxThreeImageView.isEnabled = true
                    binding.instrumentOnImageView.isEnabled = true
                    binding.plusImageView.isEnabled = true
                    binding.armZero.isEnabled = true
                    binding.chairZeroImageView.isEnabled = true

                    binding.chairFrontImageView.isEnabled = true
                    binding.chairBackImageView.isEnabled = true
                    binding.chairUpImageView.isEnabled = true
                    binding.chairDownImageView.isEnabled = true
                    binding.armUpImageView.isEnabled = true
                    binding.armDownImageView.isEnabled = true
                    binding.armFrontImageView.isEnabled = true
                    binding.armBackImageView.isEnabled = true
                }
            }
            false
        }
        binding.plusImageView.setOnClickListener {
            // mViewModel.sendMessage("57")
        }
        binding.plusImageView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    mViewModel._sendConnectivity.value = false
                    sendConnectionReq = false
                    send("57")
                    binding.plusImageView.background =
                        AppCompatResources.getDrawable(this, R.drawable.yellow_ring_bg)
                    binding.chairLockImageView.isEnabled = false
                    binding.lampImageView.isEnabled = false
                    binding.auxOneImageView.isEnabled = false
                    binding.auxTwoImageView.isEnabled = false
                    binding.auxThreeImageView.isEnabled = false
                    binding.minusImageView.isEnabled = false
                    binding.instrumentOnImageView.isEnabled = false
                    binding.chairZeroImageView.isEnabled = false
                    binding.armZero.isEnabled = false

                    binding.chairFrontImageView.isEnabled = false
                    binding.chairBackImageView.isEnabled = false
                    binding.chairUpImageView.isEnabled = false
                    binding.chairDownImageView.isEnabled = false
                    binding.armUpImageView.isEnabled = false
                    binding.armDownImageView.isEnabled = false
                    binding.armFrontImageView.isEnabled = false
                    binding.armBackImageView.isEnabled = false
                }
                MotionEvent.ACTION_UP -> {
                    mViewModel._sendConnectivity.value = true
                    sendConnectionReq = true
                    setConnectionRequestTrue()
                    binding.plusImageView.setBackgroundColor(Color.TRANSPARENT)
                    binding.chairLockImageView.isEnabled = true
                    binding.lampImageView.isEnabled = true
                    binding.auxOneImageView.isEnabled = true
                    binding.auxTwoImageView.isEnabled = true
                    binding.auxThreeImageView.isEnabled = true
                    binding.instrumentOnImageView.isEnabled = true
                    binding.minusImageView.isEnabled = true
                    binding.armZero.isEnabled = true
                    binding.chairZeroImageView.isEnabled = true

                    binding.chairFrontImageView.isEnabled = true
                    binding.chairBackImageView.isEnabled = true
                    binding.chairUpImageView.isEnabled = true
                    binding.chairDownImageView.isEnabled = true
                    binding.armUpImageView.isEnabled = true
                    binding.armDownImageView.isEnabled = true
                    binding.armFrontImageView.isEnabled = true
                    binding.armBackImageView.isEnabled = true
                }
            }
            false
        }
        binding.chairZeroImageView.setOnClickListener {
            //  mViewModel.sendMessage("08")
        }
        binding.chairZeroImageView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    Log.e("chairZeroImageView ","setOnTouchListener ")
                    mViewModel._sendConnectivity.value = false
                    sendConnectionReq = false
                    send("08")
                    binding.chairZeroImageView.background =
                        AppCompatResources.getDrawable(this, R.drawable.yellow_solid_bg)
                    binding.chairLockImageView.isEnabled = false
                    binding.lampImageView.isEnabled = false
                    binding.auxOneImageView.isEnabled = false
                    binding.auxTwoImageView.isEnabled = false
                    binding.auxThreeImageView.isEnabled = false
                    binding.minusImageView.isEnabled = false
                    binding.instrumentOnImageView.isEnabled = false
                    binding.plusImageView.isEnabled = false
                    binding.armZero.isEnabled = false

                    binding.chairFrontImageView.isEnabled = false
                    binding.chairBackImageView.isEnabled = false
                    binding.chairUpImageView.isEnabled = false
                    binding.chairDownImageView.isEnabled = false
                    binding.armUpImageView.isEnabled = false
                    binding.armDownImageView.isEnabled = false
                    binding.armFrontImageView.isEnabled = false
                    binding.armBackImageView.isEnabled = false

                }
                MotionEvent.ACTION_UP -> {
                    mViewModel._sendConnectivity.value = true
                    send("30")
                    sendConnectionReq = true
                    setConnectionRequestTrue()
                    binding.chairZeroImageView.setBackgroundColor(Color.TRANSPARENT)
                    binding.chairLockImageView.isEnabled = true
                    binding.lampImageView.isEnabled = true
                    binding.auxOneImageView.isEnabled = true
                    binding.auxTwoImageView.isEnabled = true
                    binding.auxThreeImageView.isEnabled = true
                    binding.minusImageView.isEnabled = true
                    binding.instrumentOnImageView.isEnabled = true
                    binding.plusImageView.isEnabled = true
                    binding.armZero.isEnabled = true

                    binding.chairFrontImageView.isEnabled = true
                    binding.chairBackImageView.isEnabled = true
                    binding.chairUpImageView.isEnabled = true
                    binding.chairDownImageView.isEnabled = true
                    binding.armUpImageView.isEnabled = true
                    binding.armDownImageView.isEnabled = true
                    binding.armFrontImageView.isEnabled = true
                    binding.armBackImageView.isEnabled = true
                }
            }
            false
        }
        binding.armZero.setOnClickListener {
            //   mViewModel.sendMessage("07")
        }
        binding.armZero.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    mViewModel._sendConnectivity.value = false
                    sendConnectionReq = false
                    send("07")
                    binding.armZero.background =
                        AppCompatResources.getDrawable(this, R.drawable.yellow_solid_bg)
                    binding.chairLockImageView.isEnabled = false
                    binding.lampImageView.isEnabled = false
                    binding.auxOneImageView.isEnabled = false
                    binding.auxTwoImageView.isEnabled = false
                    binding.auxThreeImageView.isEnabled = false
                    binding.minusImageView.isEnabled = false
                    binding.instrumentOnImageView.isEnabled = false
                    binding.plusImageView.isEnabled = false
                    binding.chairZeroImageView.isEnabled = false

                    binding.chairFrontImageView.isEnabled = false
                    binding.chairBackImageView.isEnabled = false
                    binding.chairUpImageView.isEnabled = false
                    binding.chairDownImageView.isEnabled = false
                    binding.armUpImageView.isEnabled = false
                    binding.armDownImageView.isEnabled = false
                    binding.armFrontImageView.isEnabled = false
                    binding.armBackImageView.isEnabled = false
                }
                MotionEvent.ACTION_UP -> {
                    mViewModel._sendConnectivity.value = true
                    sendConnectionReq = true
                    setConnectionRequestTrue()
                    binding.armZero.setBackgroundColor(Color.TRANSPARENT)
                    binding.chairLockImageView.isEnabled = true
                    binding.lampImageView.isEnabled = true
                    binding.auxOneImageView.isEnabled = true
                    binding.auxTwoImageView.isEnabled = true
                    binding.auxThreeImageView.isEnabled = true
                    binding.instrumentOnImageView.isEnabled = true
                    binding.plusImageView.isEnabled = true
                    binding.minusImageView.isEnabled = true
                    binding.chairZeroImageView.isEnabled = true

                    binding.chairFrontImageView.isEnabled = true
                    binding.chairBackImageView.isEnabled = true
                    binding.chairUpImageView.isEnabled = true
                    binding.chairDownImageView.isEnabled = true
                    binding.armUpImageView.isEnabled = true
                    binding.armDownImageView.isEnabled = true
                    binding.armFrontImageView.isEnabled = true
                    binding.armBackImageView.isEnabled = true
                }
            }
            false
        }
        binding.armFrontImageView.setOnClickListener {
        }
        binding.armFrontImageView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    mViewModel._sendConnectivity.value = false
                    binding.armBackImageView.isEnabled = false
                    sendConnectionReq = false
                    send("23")
                    binding.armFrontImageView.background =
                        AppCompatResources.getDrawable(this, R.drawable.yellow_ring_bg)
                    armFront()
                    binding.chairLockImageView.isEnabled = false
                    binding.lampImageView.isEnabled = false
                    binding.auxOneImageView.isEnabled = false
                    binding.auxTwoImageView.isEnabled = false
                    binding.auxThreeImageView.isEnabled = false
                    binding.minusImageView.isEnabled = false
                    binding.instrumentOnImageView.isEnabled = false
                    binding.plusImageView.isEnabled = false
                    binding.chairZeroImageView.isEnabled = false
                    binding.armZero.isEnabled = false

                    binding.chairFrontImageView.isEnabled = false
                    binding.chairBackImageView.isEnabled = false
                    binding.chairUpImageView.isEnabled = false
                    binding.chairDownImageView.isEnabled = false
                    binding.armUpImageView.isEnabled = false
                    binding.armDownImageView.isEnabled = false
                    binding.armBackImageView.isEnabled = false
                }
                MotionEvent.ACTION_UP -> {
                    mViewModel._sendConnectivity.value = true
                    binding.armFrontImageView.setBackgroundColor(Color.TRANSPARENT)
                    handlerArmFront.removeCallbacks(runArmFront)
                    sendConnectionReq = true
                    setConnectionRequestTrue()
                    binding.chairLockImageView.isEnabled = true
                    binding.lampImageView.isEnabled = true
                    binding.auxOneImageView.isEnabled = true
                    binding.auxTwoImageView.isEnabled = true
                    binding.auxThreeImageView.isEnabled = true
                    binding.instrumentOnImageView.isEnabled = true
                    binding.plusImageView.isEnabled = true
                    binding.minusImageView.isEnabled = true
                    binding.chairZeroImageView.isEnabled = true
                    binding.armZero.isEnabled = true

                    binding.armBackImageView.isEnabled = true
                    binding.chairFrontImageView.isEnabled = true
                    binding.chairBackImageView.isEnabled = true
                    binding.chairUpImageView.isEnabled = true
                    binding.chairDownImageView.isEnabled = true
                    binding.armUpImageView.isEnabled = true
                    binding.armDownImageView.isEnabled = true
                    binding.armBackImageView.isEnabled = true
                }
            }
            false
        }
        binding.armBackImageView.setOnClickListener {
        }
        binding.armBackImageView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    mViewModel._sendConnectivity.value = false
                    sendConnectionReq = false
                    send("21")
                    binding.armBackImageView.background =
                        AppCompatResources.getDrawable(this, R.drawable.yellow_ring_bg)
                    armBack()

                    binding.chairLockImageView.isEnabled = false
                    binding.lampImageView.isEnabled = false
                    binding.auxOneImageView.isEnabled = false
                    binding.auxTwoImageView.isEnabled = false
                    binding.auxThreeImageView.isEnabled = false
                    binding.minusImageView.isEnabled = false
                    binding.instrumentOnImageView.isEnabled = false
                    binding.plusImageView.isEnabled = false
                    binding.chairZeroImageView.isEnabled = false
                    binding.armZero.isEnabled = false

                    binding.chairFrontImageView.isEnabled = false
                    binding.chairBackImageView.isEnabled = false
                    binding.chairUpImageView.isEnabled = false
                    binding.chairDownImageView.isEnabled = false
                    binding.armUpImageView.isEnabled = false
                    binding.armDownImageView.isEnabled = false
                    binding.armFrontImageView.isEnabled = false
                }
                MotionEvent.ACTION_UP -> {
                    mViewModel._sendConnectivity.value = true
                    binding.armBackImageView.setBackgroundColor(Color.TRANSPARENT)
                    handlerArmBack.removeCallbacks(runArmBack)
                    sendConnectionReq = true
                    setConnectionRequestTrue()
                    binding.chairLockImageView.isEnabled = true
                    binding.lampImageView.isEnabled = true
                    binding.auxOneImageView.isEnabled = true
                    binding.auxTwoImageView.isEnabled = true
                    binding.auxThreeImageView.isEnabled = true
                    binding.instrumentOnImageView.isEnabled = true
                    binding.plusImageView.isEnabled = true
                    binding.minusImageView.isEnabled = true
                    binding.chairZeroImageView.isEnabled = true
                    binding.armZero.isEnabled = true

                    binding.chairFrontImageView.isEnabled = true
                    binding.chairBackImageView.isEnabled = true
                    binding.chairUpImageView.isEnabled = true
                    binding.chairDownImageView.isEnabled = true
                    binding.armUpImageView.isEnabled = true
                    binding.armDownImageView.isEnabled = true
                    binding.armFrontImageView.isEnabled = true
                }
            }
            false
        }
        binding.armUpImageView.setOnClickListener {
            //mViewModel.sendMessage("20")
        }
        binding.armUpImageView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    mViewModel._sendConnectivity.value = false
                    sendConnectionReq = false
                    send("20")
                    binding.armUpImageView.background =
                        AppCompatResources.getDrawable(this, R.drawable.yellow_ring_bg)
                    armUp()
                    binding.chairLockImageView.isEnabled = false
                    binding.lampImageView.isEnabled = false
                    binding.auxOneImageView.isEnabled = false
                    binding.auxTwoImageView.isEnabled = false
                    binding.auxThreeImageView.isEnabled = false
                    binding.minusImageView.isEnabled = false
                    binding.instrumentOnImageView.isEnabled = false
                    binding.plusImageView.isEnabled = false
                    binding.chairZeroImageView.isEnabled = false
                    binding.armZero.isEnabled = false

                    binding.chairFrontImageView.isEnabled = false
                    binding.chairBackImageView.isEnabled = false
                    binding.chairUpImageView.isEnabled = false
                    binding.chairDownImageView.isEnabled = false
                    binding.armDownImageView.isEnabled = false
                    binding.armFrontImageView.isEnabled = false
                    binding.armBackImageView.isEnabled = false
                }
                MotionEvent.ACTION_UP -> {
                    mViewModel._sendConnectivity.value = true
                    binding.armUpImageView.setBackgroundColor(Color.TRANSPARENT)
                    handlerArmUp.removeCallbacks(runArmUp)
                    sendConnectionReq = true
                    setConnectionRequestTrue()
                    binding.chairLockImageView.isEnabled = true
                    binding.lampImageView.isEnabled = true
                    binding.auxOneImageView.isEnabled = true
                    binding.auxTwoImageView.isEnabled = true
                    binding.auxThreeImageView.isEnabled = true
                    binding.instrumentOnImageView.isEnabled = true
                    binding.plusImageView.isEnabled = true
                    binding.minusImageView.isEnabled = true
                    binding.chairZeroImageView.isEnabled = true
                    binding.armZero.isEnabled = true

                    binding.chairFrontImageView.isEnabled = true
                    binding.chairBackImageView.isEnabled = true
                    binding.chairUpImageView.isEnabled = true
                    binding.chairDownImageView.isEnabled = true
                    binding.armDownImageView.isEnabled = true
                    binding.armFrontImageView.isEnabled = true
                    binding.armBackImageView.isEnabled = true
                }
            }
            false
        }
        binding.armDownImageView.setOnClickListener {
            //   mViewModel.sendMessage("22")
        }
        binding.armDownImageView.setOnTouchListener { v, event ->
            mViewModel._sendConnectivity.value = false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    sendConnectionReq = false
                    send("22")
                    binding.armDownImageView.background =
                        AppCompatResources.getDrawable(this, R.drawable.yellow_ring_bg)
                    armDown()
                    binding.chairLockImageView.isEnabled = false
                    binding.lampImageView.isEnabled = false
                    binding.auxOneImageView.isEnabled = false
                    binding.auxTwoImageView.isEnabled = false
                    binding.auxThreeImageView.isEnabled = false
                    binding.minusImageView.isEnabled = false
                    binding.instrumentOnImageView.isEnabled = false
                    binding.plusImageView.isEnabled = false
                    binding.chairZeroImageView.isEnabled = false
                    binding.armZero.isEnabled = false

                    binding.chairFrontImageView.isEnabled = false
                    binding.chairBackImageView.isEnabled = false
                    binding.chairUpImageView.isEnabled = false
                    binding.chairDownImageView.isEnabled = false
                    binding.armUpImageView.isEnabled = false
                    binding.armFrontImageView.isEnabled = false
                    binding.armBackImageView.isEnabled = false
                }
                MotionEvent.ACTION_UP -> {
                    mViewModel._sendConnectivity.value = true
                    binding.armDownImageView.setBackgroundColor(Color.TRANSPARENT)
                    handlerArmDown.removeCallbacks(runArmDown)
                    sendConnectionReq = true
                    setConnectionRequestTrue()
                    binding.chairLockImageView.isEnabled = true
                    binding.lampImageView.isEnabled = true
                    binding.auxOneImageView.isEnabled = true
                    binding.auxTwoImageView.isEnabled = true
                    binding.auxThreeImageView.isEnabled = true
                    binding.instrumentOnImageView.isEnabled = true
                    binding.plusImageView.isEnabled = true
                    binding.minusImageView.isEnabled = true
                    binding.chairZeroImageView.isEnabled = true
                    binding.armZero.isEnabled = true


                    binding.chairFrontImageView.isEnabled = true
                    binding.chairBackImageView.isEnabled = true
                    binding.chairUpImageView.isEnabled = true
                    binding.chairDownImageView.isEnabled = true
                    binding.armUpImageView.isEnabled = true
                    binding.armFrontImageView.isEnabled = true
                    binding.armBackImageView.isEnabled = true
                }
            }
            false
        }
    }


    private fun observeConnectivity() {
        mViewModel.sendConnectivity.observe(this, Observer {
            if (it) {
                send("30")
            }
        })
    }

    private fun send(str: String) {
        Log.e("send send ", "" + str)
        if (connected != Connected.True) {
            // Toast.makeText(this, "not connected", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val msg: String
            val data: ByteArray
            if (hexEnabled) {
                val sb = StringBuilder()
                TextUtil.toHexString(sb, TextUtil.fromHexString(str))
                TextUtil.toHexString(sb, newline.toByteArray())
                msg = sb.toString()
                data = TextUtil.fromHexString(msg)
            } else {
                msg = str
                data = (str + newline).toByteArray()
            }
            val spn = SpannableStringBuilder(
                """
                  $msg
                  
                  """.trimIndent()
            )
            spn.setSpan(
                ForegroundColorSpan(resources.getColor(R.color.colorSendText)),
                0,
                spn.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            service!!.write(data)
            //receiveText!!.append(spn)

        } catch (e: java.lang.Exception) {
            Log.e("onSend ", " " + e.toString())
            onSerialIoError(e)
        }
    }

    private fun receive(datas: ArrayDeque<ByteArray>) {
        val spn = SpannableStringBuilder()
        for (data in datas) {
            if (hexEnabled) {
                spn.append(TextUtil.toHexString(data)).append('\n')
            } else {
                var msg = String(data)
                if (newline == TextUtil.newline_crlf && msg.isNotEmpty()) {
                    // don't show CR as ^M if directly before LF
                    msg = msg.replace(TextUtil.newline_crlf, TextUtil.newline_lf)
                    // special handling if CR and LF come in separate fragments
                    if (pendingNewline && msg[0] == '\n') {
                        if (spn.length >= 2) {
                            spn.delete(spn.length - 2, spn.length)
                        } else {
                            val edt = receiveText!!.editableText
                            if (edt != null && edt.length >= 2) edt.delete(
                                edt.length - 2,
                                edt.length
                            )
                        }
                    }
                    pendingNewline = msg[msg.length - 1] == '\r'
                }
                spn.append(TextUtil.toCaretString(msg, newline.length != 0))
            }
        }
        setReceivedText(spn)
        Log.e("receive ", " " + spn)
        //  receiveText!!.append(spn)
    }

    private fun setReceivedText(spn: SpannableStringBuilder) {
        when (spn.toString()) {
            "C01" -> {
                binding.chairLockOnOff.setTextColor(
                    ContextCompat.getColor(this, R.color.colorPrimary)
                )
                binding.chairLockOnOff.text = getString(R.string.label_on)
            }
            "C00" -> {
                binding.chairLockOnOff.text = getString(R.string.label_off)
                binding.chairLockOnOff.setTextColor(
                    ContextCompat.getColor(
                        this,
                        R.color.colorNavyBlue
                    )
                )
            }
            "L01" -> {
                binding.lampOnOff.setTextColor(
                    ContextCompat.getColor(this, R.color.colorPrimary)
                )
                binding.lampOnOff.text = getString(R.string.label_on)
            }
            "L00" -> {
                binding.lampOnOff.text = getString(R.string.label_off)
                binding.lampOnOff.setTextColor(
                    ContextCompat.getColor(
                        this,
                        R.color.colorNavyBlue
                    )
                )
            }
            "A11" -> {
                binding.auxOneOnOff.setTextColor(
                    ContextCompat.getColor(this, R.color.colorPrimary)
                )
                binding.auxOneOnOff.text = getString(R.string.label_on)
            }
            "A10" -> {
                binding.auxOneOnOff.text = getString(R.string.label_off)
                binding.auxOneOnOff.setTextColor(
                    ContextCompat.getColor(
                        this,
                        R.color.colorNavyBlue
                    )
                )
            }
            "A21" -> {
                binding.auxTwoOnOff.setTextColor(
                    ContextCompat.getColor(this, R.color.colorPrimary)
                )
                binding.auxTwoOnOff.text = getString(R.string.label_on)
            }
            "A20" -> {
                binding.auxTwoOnOff.text = getString(R.string.label_off)
                binding.auxTwoOnOff.setTextColor(
                    ContextCompat.getColor(
                        this,
                        R.color.colorNavyBlue
                    )
                )
            }
            "A31" -> {
                binding.auxThreeOnOff.setTextColor(
                    ContextCompat.getColor(this, R.color.colorPrimary)
                )
                binding.auxThreeOnOff.text = getString(R.string.label_on)
            }
            "A30" -> {
                binding.auxThreeOnOff.text = getString(R.string.label_off)
                binding.auxThreeOnOff.setTextColor(
                    ContextCompat.getColor(
                        this,
                        R.color.colorNavyBlue
                    )
                )
            }
            "I11" -> {
                binding.instrumentOnOff.text = getString(R.string.label_on)
                binding.instrumentOnOff.setTextColor(
                    ContextCompat.getColor(
                        this,
                        R.color.colorPrimary
                    )
                )
                binding.minusImageView.isEnabled = true
                binding.plusImageView.isEnabled = true
            }
            "220" -> {
                binding.instrumentValTextView.text = "0"
                //  binding.minusImageView.isEnabled = false
            }
            "221" -> {
                binding.instrumentValTextView.text = "1"
                binding.minusImageView.isEnabled = true
            }
            "222" -> {
                binding.instrumentValTextView.text = "2"
            }
            "223" -> {
                binding.instrumentValTextView.text = "3"
            }
            "224" -> {
                binding.instrumentValTextView.text = "4"
            }
            "225" -> {
                binding.instrumentValTextView.text = "5"
            }
            "226" -> {
                binding.instrumentValTextView.text = "6"
            }
            "227" -> {
                binding.instrumentValTextView.text = "7"
            }
            "228" -> {
                binding.instrumentValTextView.text = "8"
                binding.plusImageView.isEnabled = true
            }
            "229" -> {
                binding.instrumentValTextView.text = "9"
                //  binding.plusImageView.isEnabled = false

            }
            "I10" -> {
                binding.instrumentOnOff.text = getString(R.string.label_off)
                binding.instrumentOnOff.setTextColor(
                    ContextCompat.getColor(
                        this,
                        R.color.colorNavyBlue
                    )
                )
                binding.plusImageView.isEnabled = true
                binding.minusImageView.isEnabled = true
            }
        }
    }

    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
        Log.e("onServiceConnected", "onServiceConnected")
        this.service = (binder as SerialService.SerialBinder).service
        if (this.service != null) {
            this.service!!.attach(this)
            val isActivityInForeground = this.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)

            if (initialStart && isActivityInForeground) {
                initialStart = false
                this.runOnUiThread(Runnable { connect() })
            }
        }
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        Log.e("onServiceDisconnected", "onServiceDisconnected")
        binding.connectedTextView.text = getString(R.string.disconnected)
    }

    override fun onSerialConnect() {
        Log.e("onSerialConnect", "onSerialConnect")
        connected = Connected.True
        send("30")
        binding.connectedTextView.text = getString(R.string.connected)
    }

    override fun onSerialConnectError(e: Exception?) {
        Log.e("onSerialConnectError", "onSerialConnectError  " + e.toString())
        binding.connectedTextView.text = getString(R.string.disconnected)

    }

    override fun onSerialRead(data: ByteArray?) {
        val datas = ArrayDeque<ByteArray>()
        datas.add(data)
        receive(datas)
    }

    override fun onSerialRead(datas: ArrayDeque<ByteArray>?) {
        if (datas != null) {
            receive(datas)
        }
    }

    override fun onSerialIoError(e: Exception?) {
        Log.e("onSerialIoError", "onSerialIoError "+e.toString())
        binding.connectedTextView.text = getString(R.string.disconnected)
    }


}


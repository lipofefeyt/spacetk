package com.example.spacetk.serial

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

private const val ACTION_USB_PERMISSION = "com.example.spacetk.USB_PERMISSION"
private const val WRITE_TIMEOUT_MS = 2000

sealed interface UsbConnectionState {
    data object Disconnected : UsbConnectionState
    data object RequestingPermission : UsbConnectionState
    data class Connected(val deviceName: String, val profile: TargetProfile) : UsbConnectionState
    data class Error(val message: String) : UsbConnectionState
}

/**
 * Wraps usb-serial-for-android: device enumeration, the permission-request dance (the library
 * itself doesn't handle Android USB permissions), and a single open port's RX/TX streams.
 */
class UsbSerialManager(private val context: Context) {

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val ioExecutor = Executors.newSingleThreadExecutor()

    private var port: UsbSerialPort? = null
    private var ioManager: SerialInputOutputManager? = null
    private var pendingConnect: Pair<UsbSerialDriver, TargetProfile>? = null

    private val _state = MutableStateFlow<UsbConnectionState>(UsbConnectionState.Disconnected)
    val state = _state.asStateFlow()

    private val _rxData = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    val rxData = _rxData.asSharedFlow()

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context, intent: Intent) {
            if (intent.action != ACTION_USB_PERMISSION) return
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            val (driver, profile) = pendingConnect ?: return
            pendingConnect = null
            if (granted) {
                openPort(driver, profile)
            } else {
                _state.value = UsbConnectionState.Error("USB permission denied")
            }
        }
    }

    init {
        ContextCompat.registerReceiver(
            context,
            permissionReceiver,
            IntentFilter(ACTION_USB_PERMISSION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    fun availableDrivers(): List<UsbSerialDriver> =
        UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)

    fun connect(driver: UsbSerialDriver, profile: TargetProfile) {
        val device = driver.device
        if (usbManager.hasPermission(device)) {
            openPort(driver, profile)
            return
        }
        pendingConnect = driver to profile
        _state.value = UsbConnectionState.RequestingPermission
        val permissionIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION_USB_PERMISSION),
            PendingIntent.FLAG_IMMUTABLE,
        )
        usbManager.requestPermission(device, permissionIntent)
    }

    private fun openPort(driver: UsbSerialDriver, profile: TargetProfile) {
        val connection = usbManager.openDevice(driver.device)
        if (connection == null) {
            _state.value = UsbConnectionState.Error("Could not open device (no permission, or already in use)")
            return
        }
        val serialPort = driver.ports.firstOrNull()
        if (serialPort == null) {
            _state.value = UsbConnectionState.Error("Device exposes no serial ports")
            return
        }
        try {
            serialPort.open(connection)
            serialPort.setParameters(
                profile.baudRate,
                profile.dataBits,
                profile.stopBits.toUsbStopBits(),
                profile.parity.toUsbParity(),
            )
        } catch (e: Exception) {
            try {
                serialPort.close()
            } catch (_: Exception) {
                // Best-effort cleanup; the port never fully opened.
            }
            _state.value = UsbConnectionState.Error("Failed to configure port: ${e.message}")
            return
        }

        port = serialPort
        val listener = object : SerialInputOutputManager.Listener {
            override fun onNewData(data: ByteArray) {
                _rxData.tryEmit(data)
            }

            override fun onRunError(e: Exception) {
                _state.value = UsbConnectionState.Error("Connection lost: ${e.message}")
                disconnect()
            }
        }
        val manager = SerialInputOutputManager(serialPort, listener)
        ioManager = manager
        ioExecutor.submit(manager)
        _state.value = UsbConnectionState.Connected(driver.device.deviceName, profile)
    }

    fun send(bytes: ByteArray) {
        port?.write(bytes, WRITE_TIMEOUT_MS)
    }

    fun disconnect() {
        ioManager?.stop()
        ioManager = null
        try {
            port?.close()
        } catch (_: Exception) {
            // Already closed, e.g. device was physically unplugged.
        }
        port = null
        if (_state.value !is UsbConnectionState.Error) {
            _state.value = UsbConnectionState.Disconnected
        }
    }

    /** Call from onDestroy to release the permission receiver and I/O thread. */
    fun dispose() {
        disconnect()
        context.unregisterReceiver(permissionReceiver)
        ioExecutor.shutdown()
    }
}

private fun Int.toUsbStopBits(): Int = when (this) {
    1 -> UsbSerialPort.STOPBITS_1
    2 -> UsbSerialPort.STOPBITS_2
    else -> UsbSerialPort.STOPBITS_1
}

private fun SerialParity.toUsbParity(): Int = when (this) {
    SerialParity.NONE -> UsbSerialPort.PARITY_NONE
    SerialParity.ODD -> UsbSerialPort.PARITY_ODD
    SerialParity.EVEN -> UsbSerialPort.PARITY_EVEN
}

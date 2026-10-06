package com.alarmissimo.ui.viewmodel

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alarmissimo.data.AlarmRepository
import com.alarmissimo.data.model.AcceptedDevice
import com.alarmissimo.data.model.SoundDeviceConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Minimal info about a bonded Bluetooth device for display.
 * [isCurrentlyConnected] may be stale — it's a best-effort check at load time.
 */
data class BtDeviceInfo(
    val mac: String,
    val name: String,
    val isCurrentlyConnected: Boolean
)

/** UI state for the Sound Device screen. */
data class SoundDeviceUiState(
    val config: SoundDeviceConfig = SoundDeviceConfig(),
    /** All bonded BT devices visible on the system (requires BLUETOOTH_CONNECT permission). */
    val bondedDevices: List<BtDeviceInfo> = emptyList(),
    val hasBluetoothPermission: Boolean = false,
    val isSaved: Boolean = false
)

/**
 * ViewModel for the Sound Device configuration screen.
 *
 * Manages both BT warning and keep-alive settings.
 */
class SoundDeviceViewModel(private val repository: AlarmRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(SoundDeviceUiState())
    val uiState: StateFlow<SoundDeviceUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.soundDeviceConfigFlow.collect { config ->
                _uiState.value = _uiState.value.copy(config = config)
            }
        }
    }

    fun update(config: SoundDeviceConfig) {
        _uiState.value = _uiState.value.copy(config = config)
    }

    fun save() {
        viewModelScope.launch {
            repository.saveSoundDeviceConfig(_uiState.value.config)
            _uiState.value = _uiState.value.copy(isSaved = true)
        }
    }

    /** Loads bonded BT devices from [BluetoothManager]. Requires BLUETOOTH_CONNECT permission on API 31+. */
    fun loadBondedDevices(context: Context) {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        _uiState.value = _uiState.value.copy(hasBluetoothPermission = hasPermission)
        if (!hasPermission) return

        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = btManager.adapter ?: return
        @Suppress("MissingPermission")
        val bonded = try {
            adapter.bondedDevices?.map { device ->
                val connected = try {
                    val method = device.javaClass.getMethod("isConnected")
                    method.invoke(device) as? Boolean ?: false
                } catch (e: Exception) { false }
                BtDeviceInfo(mac = device.address, name = device.name ?: device.address, isCurrentlyConnected = connected)
            } ?: emptyList()
        } catch (e: SecurityException) {
            emptyList()
        }
        _uiState.value = _uiState.value.copy(bondedDevices = bonded)
    }

    /** Adds [device] to the accepted devices list and saves immediately. */
    fun addAcceptedDevice(device: BtDeviceInfo) {
        val current = _uiState.value.config
        if (current.acceptedDevices.any { it.mac == device.mac }) return
        val updated = current.copy(
            acceptedDevices = current.acceptedDevices + AcceptedDevice(device.mac, device.name)
        )
        _uiState.value = _uiState.value.copy(config = updated)
        viewModelScope.launch { repository.saveSoundDeviceConfig(updated) }
    }

    /** Removes the device with [mac] from the accepted list and saves. */
    fun removeAcceptedDevice(mac: String) {
        val current = _uiState.value.config
        val updated = current.copy(acceptedDevices = current.acceptedDevices.filter { it.mac != mac })
        _uiState.value = _uiState.value.copy(config = updated)
        viewModelScope.launch { repository.saveSoundDeviceConfig(updated) }
    }
}

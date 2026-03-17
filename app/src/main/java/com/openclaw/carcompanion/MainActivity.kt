package com.openclaw.carcompanion

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.openclaw.carcompanion.databinding.ActivityMainBinding
import com.openclaw.carcompanion.service.CarCompanionService
import kotlinx.coroutines.launch

/**
 * 主活動 - 顯示狀態與控制界面
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private var carService: CarCompanionService? = null
    private var serviceBound = false

    // 權限請求
    private val requiredPermissions = mutableListOf(
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            startService()
        } else {
            Toast.makeText(this, "需要所有權限才能正常運作", Toast.LENGTH_LONG).show()
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as CarCompanionService.LocalBinder
            carService = binder.getService()
            serviceBound = true
            observeServiceState()
            Log.d(TAG, "Service connected")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            carService = null
            serviceBound = false
            Log.d(TAG, "Service disconnected")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        checkPermissions()
    }

    override fun onStart() {
        super.onStart()
        bindService()
    }

    override fun onStop() {
        super.onStop()
        unbindService()
    }

    private fun setupUI() {
        // 停止 TTS 按鈕
        binding.btnStopTts.setOnClickListener {
            carService?.stopTts()
            Toast.makeText(this, "已停止朗讀", Toast.LENGTH_SHORT).show()
        }

        // 重新連線按鈕
        binding.btnReconnect.setOnClickListener {
            checkPermissions()
        }

        // 設定按鈕 (預留)
        binding.btnSettings.setOnClickListener {
            showSettingsDialog()
        }
    }

    private fun checkPermissions() {
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            startService()
        } else {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private fun startService() {
        val intent = Intent(this, CarCompanionService::class.java).apply {
            action = CarCompanionService.ACTION_START
            // 可以從設定中讀取 Gateway 配置
            putExtra(CarCompanionService.EXTRA_GATEWAY_HOST, "43.134.169.83")
            putExtra(CarCompanionService.EXTRA_GATEWAY_PORT, 18789)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun bindService() {
        val intent = Intent(this, CarCompanionService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun unbindService() {
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
    }

    private fun observeServiceState() {
        carService?.let { service ->
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    launch {
                        service.connectionState.collect { state ->
                            updateConnectionUI(state)
                        }
                    }
                    launch {
                        service.gpsEnabled.collect { enabled ->
                            updateGpsUI(enabled)
                        }
                    }
                }
            }
        }
    }

    private fun updateConnectionUI(state: CarCompanionService.ConnectionState) {
        runOnUiThread {
            when (state) {
                CarCompanionService.ConnectionState.Connecting -> {
                    binding.tvStatus.text = "連線中..."
                    binding.tvStatus.setTextColor(getColor(R.color.status_connecting))
                    binding.ivStatus.setImageResource(R.drawable.ic_status_connecting)
                }
                CarCompanionService.ConnectionState.Connected -> {
                    binding.tvStatus.text = "已連線"
                    binding.tvStatus.setTextColor(getColor(R.color.status_connected))
                    binding.ivStatus.setImageResource(R.drawable.ic_status_connected)
                    binding.btnReconnect.isEnabled = true
                }
                CarCompanionService.ConnectionState.Disconnected -> {
                    binding.tvStatus.text = "未連線"
                    binding.tvStatus.setTextColor(getColor(R.color.status_disconnected))
                    binding.ivStatus.setImageResource(R.drawable.ic_status_disconnected)
                    binding.btnReconnect.isEnabled = true
                }
                is CarCompanionService.ConnectionState.Error -> {
                    binding.tvStatus.text = "錯誤: ${state.message}"
                    binding.tvStatus.setTextColor(getColor(R.color.status_error))
                    binding.ivStatus.setImageResource(R.drawable.ic_status_error)
                    binding.btnReconnect.isEnabled = true
                }
            }
        }
    }

    private fun updateGpsUI(enabled: Boolean) {
        runOnUiThread {
            binding.tvGpsStatus.text = if (enabled) "GPS: 已啟用" else "GPS: 未啟用"
            binding.tvGpsStatus.setTextColor(
                getColor(if (enabled) R.color.status_connected else R.color.status_disconnected)
            )
        }
    }

    private fun showSettingsDialog() {
        // 預留：可添加 Gateway 設定對話框
        Toast.makeText(this, "設定功能開發中", Toast.LENGTH_SHORT).show()
    }
}

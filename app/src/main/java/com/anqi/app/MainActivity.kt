package com.anqi.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import com.anqi.app.ui.AnqiRoot
import com.anqi.app.ui.theme.AnqiTheme
import com.anqi.app.viewmodel.AlarmViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: AlarmViewModel by lazy {
        ViewModelProvider(this)[AlarmViewModel::class.java]
    }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            viewModel.refreshReadiness()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AnqiTheme {
                AnqiRoot(
                    viewModel = viewModel,
                    onRequestNotifications = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onOpenExactAlarmSettings = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                .setData("package:$packageName".toUri())
                            runCatching { startActivity(intent) }
                                .onFailure { startActivity(Intent(Settings.ACTION_SETTINGS)) }
                        } else {
                            startActivity(Intent(Settings.ACTION_SETTINGS))
                        }
                    },
                    onOpenFullScreenSettings = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                                .setData("package:$packageName".toUri())
                            runCatching { startActivity(intent) }
                                .onFailure { startActivity(Intent(Settings.ACTION_SETTINGS)) }
                        } else {
                            openAppSettings()
                        }
                    },
                    onOpenAppSettings = ::openAppSettings
                )
            }
        }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData("package:$packageName".toUri())
        runCatching { startActivity(intent) }
            .onFailure { startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshReadiness()
    }
}

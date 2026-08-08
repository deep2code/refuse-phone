package com.example.phonequery

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.phonequery.ui.PhoneQueryViewModel
import com.example.phonequery.ui.settings.PhoneQueryApp
import com.example.phonequery.ui.settings.SettingsViewModel
import com.example.phonequery.ui.theme.PhoneQueryTheme

class MainActivity : ComponentActivity() {

    private val viewModel: PhoneQueryViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    private val requiredPermissions = mutableListOf<String>().apply {
        add(Manifest.permission.READ_PHONE_STATE)
        add(Manifest.permission.READ_CALL_LOG)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            add(Manifest.permission.ANSWER_PHONE_CALLS)
        }
    }.toTypedArray()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val denied = results.filter { !it.value }.keys
        if (denied.isNotEmpty()) {
            Toast.makeText(
                this,
                R.string.permission_denied_hint,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (requiredPermissions.any {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }) {
            permissionLauncher.launch(requiredPermissions)
        }

        setContent {
            PhoneQueryTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PhoneQueryApp(
                        queryViewModel = viewModel,
                        settingsViewModel = settingsViewModel
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 保持前台服务运行，不在这里 stopService
    }
}

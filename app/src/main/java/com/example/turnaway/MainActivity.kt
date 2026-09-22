package com.example.turnaway

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.example.turnaway.data.db.SoftLandingDatabase
import com.example.turnaway.data.repository.SoftLandingRepository
import com.example.turnaway.security.BiometricSecurityGate
import com.example.turnaway.ui.screens.ParentDashboardScreen
import com.example.turnaway.ui.theme.TurnAwayTheme
import com.example.turnaway.ui.viewmodel.DashboardViewModel
import com.example.turnaway.ui.viewmodel.DashboardViewModelFactory
import com.example.turnaway.util.AppLogger

class MainActivity : FragmentActivity() {

    private lateinit var viewModel: DashboardViewModel
    private lateinit var biometricSecurityGate: BiometricSecurityGate

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppLogger.i("MainActivity", "Soft-Landing Parental Portal onCreate initialized")

        val database = SoftLandingDatabase.getDatabase(applicationContext)
        val repository = SoftLandingRepository(database.softLandingDao())
        val factory = DashboardViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[DashboardViewModel::class.java]

        biometricSecurityGate = BiometricSecurityGate(this)

        setContent {
            TurnAwayTheme {
                ParentDashboardScreen(
                    viewModel = viewModel,
                    onRequestBiometricAuth = {
                        biometricSecurityGate.authenticate(
                            onSuccess = {
                                viewModel.unlockDashboard()
                            },
                            onError = { error ->
                                AppLogger.w("Biometrics", "Biometric authentication failed or cancelled: $error")
                                Toast.makeText(this, "Authentication failed: $error", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                )
            }
        }

        viewModel.checkPermissions(this)
    }

    override fun onResume() {
        super.onResume()
        if (::viewModel.isInitialized) {
            viewModel.checkPermissions(this)
        }
    }

    fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }
}
package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.VmState
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.RemoteDesktopScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.CloudPocketViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: CloudPocketViewModel = viewModel()
                    val vmStatus by viewModel.vmStatus.collectAsState()
                    val currentFrame by viewModel.currentFrame.collectAsState()

                    // If VM is running or reconnecting with an existing stream frame, render the remote desktop
                    if (vmStatus.state == VmState.RUNNING || (vmStatus.state == VmState.RECONNECTING && currentFrame != null)) {
                        RemoteDesktopScreen(viewModel = viewModel)
                    } else {
                        HomeScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}

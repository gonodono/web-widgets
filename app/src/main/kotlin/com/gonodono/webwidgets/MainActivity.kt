package com.gonodono.webwidgets

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri

class MainActivity : ComponentActivity() {

    private lateinit var hasPermission: MutableState<Boolean>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        hasPermission = mutableStateOf(Settings.canDrawOverlays(this))

        setContent {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                Text("Web Widgets", fontSize = 28.sp)
                Spacer(Modifier.size(10.dp))
                if (hasPermission.value) {
                    Text("Permission granted")
                } else {
                    Text("Overlay permission required")
                    Spacer(Modifier.size(10.dp))
                    Button(::openPermissions) { Text("Open permissions") }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hasPermission.value = Settings.canDrawOverlays(this)
    }

    private fun openPermissions() = startActivity(
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            "package:$packageName".toUri()
        )
    )
}
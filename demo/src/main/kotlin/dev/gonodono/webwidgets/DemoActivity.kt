package dev.gonodono.webwidgets

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.application
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class DemoActivity : ComponentActivity() {

    private val uiModel: UiModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DemoContent(Modifier.fillMaxSize(), uiModel) }
    }

    override fun onResume() {
        super.onResume()
        uiModel.update()
    }
}

@Composable
private fun DemoContent(
    modifier: Modifier = Modifier,
    uiModel: UiModel = viewModel()
) {
    val uiState by uiModel.uiState.collectAsState()

    Box(contentAlignment = Alignment.Center, modifier = modifier) {

        Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
            Text(
                text = "Web Widgets",
                fontSize = 36.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            HorizontalDivider(
                modifier = Modifier
                    .width(200.dp)
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(Modifier.size(5.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.choosable { uiModel.toggleShooter() }
            ) {
                RadioButton(
                    selected = uiState.useVirtualWebShooter,
                    onClick = { uiModel.toggleShooter() }
                )
                Text("Use VirtualWebShooter")
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.choosable { uiModel.toggleShooter() }
            ) {
                RadioButton(
                    selected = !uiState.useVirtualWebShooter,
                    onClick = { uiModel.toggleShooter() }
                )
                Text("Use OverlayWebShooter")
            }

            if (!uiState.hasOverlayPermission) {
                val context = LocalContext.current
                Button(
                    onClick = { context.openManageOverlayPermission() },
                    enabled = !uiState.useVirtualWebShooter
                ) {
                    Text("Overlay permission required")
                }
            }

            Spacer(Modifier.size(5.dp))

            HorizontalDivider(
                modifier = Modifier
                    .width(200.dp)
                    .align(Alignment.CenterHorizontally)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.choosable { uiModel.toggleLabel() }
            ) {
                Checkbox(uiState.drawTypeLabel, { uiModel.toggleLabel() })
                Text("Draw type label")
            }
        }
    }
}

internal data class UiState(
    val useVirtualWebShooter: Boolean,
    val hasOverlayPermission: Boolean,
    val drawTypeLabel: Boolean
)

internal class UiModel(application: Application) :
    AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(application.currentState())
    internal val uiState: StateFlow<UiState> get() = _uiState

    fun update() {
        _uiState.value = application.currentState()
    }

    private val settings = application.appSettings

    fun toggleShooter() {
        settings.useVirtualWebShooter = !settings.useVirtualWebShooter
        update()
    }

    fun toggleLabel() {
        settings.drawTypeLabel = !settings.drawTypeLabel
        update()
    }
}

private fun Context.currentState(): UiState {
    val appSettings = this.appSettings
    return UiState(
        useVirtualWebShooter = appSettings.useVirtualWebShooter,
        hasOverlayPermission = Settings.canDrawOverlays(this),
        drawTypeLabel = appSettings.drawTypeLabel
    )
}

private fun Context.openManageOverlayPermission() {
    val uri = "package:${this.packageName}".toUri()
    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, uri)
    this.startActivity(intent)
}

@Stable
private fun Modifier.choosable(onClick: () -> Unit): Modifier =
    this.composed {
        clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    }
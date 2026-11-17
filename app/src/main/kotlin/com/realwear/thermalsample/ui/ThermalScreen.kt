package com.realwear.thermalsample.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.realwear.thermalsample.R
import com.realwear.thermalsample.ThermalStatus
import com.realwear.thermalsample.ThermalUiState
import com.realwear.thermalsample.thermal.ThermalSettings.ObjectDistance

@Composable
fun ThermalScreen(
    state: ThermalUiState,
    hasCameraPermission: Boolean,
    onRequestPermission: () -> Unit,
    onObjectDistance: (ObjectDistance) -> Unit,
    onTakePicture: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // The crosshair and the temperature are drawn into the frame by TemperatureOverlay.
        state.frame?.let { frame ->
            val image = remember(frame) { frame.asImageBitmap() }
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = statusText(state.status, hasCameraPermission), color = Color.White, fontSize = 18.sp)
            Text(text = stringResource(R.string.sdk_version, state.sdkVersion), color = Color.LightGray, fontSize = 14.sp)
        }

        if (state.isCalibrating) {
            Text(
                text = stringResource(R.string.calibrating),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(text = pictureText(state), color = Color.White, fontSize = 14.sp)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (hasCameraPermission) {
                    ObjectDistance.entries.forEach { distance ->
                        DistanceButton(
                            label = stringResource(distance.labelRes()),
                            isSelected = distance == state.objectDistance,
                            onClick = { onObjectDistance(distance) },
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = onTakePicture, enabled = state.status == ThermalStatus.Streaming) {
                        Text(stringResource(R.string.take_picture))
                    }
                } else {
                    Button(onClick = onRequestPermission) {
                        Text(stringResource(R.string.grant_camera))
                    }
                }
            }
        }
    }
}

@Composable
private fun DistanceButton(label: String, isSelected: Boolean, onClick: () -> Unit) {
    if (isSelected) {
        Button(onClick = onClick) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick) { Text(label, color = Color.White) }
    }
}

private fun ObjectDistance.labelRes(): Int = when (this) {
    ObjectDistance.NEAR -> R.string.distance_near
    ObjectDistance.MIDDLE -> R.string.distance_middle
    ObjectDistance.FAR -> R.string.distance_far
}

@Composable
private fun statusText(status: ThermalStatus, hasCameraPermission: Boolean): String {
    if (!hasCameraPermission) return stringResource(R.string.status_no_permission)
    return when (status) {
        is ThermalStatus.Error -> stringResource(R.string.status_error, status.message)
        ThermalStatus.NoModule -> stringResource(R.string.status_no_module)
        ThermalStatus.Starting -> stringResource(R.string.status_starting)
        ThermalStatus.Searching -> stringResource(R.string.status_searching)
        ThermalStatus.Connecting -> stringResource(R.string.status_connecting)
        ThermalStatus.Streaming -> stringResource(R.string.status_streaming)
    }
}

@Composable
private fun pictureText(state: ThermalUiState): String = when {
    state.pictureFailed -> stringResource(R.string.picture_failed)
    state.lastPicture != null -> stringResource(R.string.picture_saved, state.lastPicture)
    else -> ""
}

@Preview(widthDp = 854, heightDp = 480)
@Composable
private fun ThermalScreenPreview() {
    ThermalScreen(
        state = ThermalUiState(status = ThermalStatus.Streaming, sdkVersion = "2.24.0", isCalibrating = true),
        hasCameraPermission = true,
        onRequestPermission = {},
        onObjectDistance = {},
        onTakePicture = {},
    )
}

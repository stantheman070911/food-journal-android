package com.stanley.foodjournal.ui.meal

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Size
import android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
import android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
import androidx.activity.compose.LocalActivity
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.stanley.foodjournal.R
import com.stanley.foodjournal.ui.icons.FoodJournalIcons
import kotlinx.coroutines.awaitCancellation
import java.io.File

// The camera is always dark, whatever the app theme, so the photo is all there is to look at.
private val CameraBackground = Color.Black
private val CameraInk = Color.White
private val CameraGrey = Color(0xFF8C8C8C)
private val SideControlWidth = 80.dp

/** Close to the stored size, so a capture needs little resizing. Sizes are in sensor landscape. */
private val CaptureResolution = ResolutionSelector.Builder()
    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
    .setResolutionStrategy(
        ResolutionStrategy(Size(2048, 1536), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
    )
    .build()

/**
 * The first step of logging: a 3:4 viewfinder with the shutter, a gallery button beside it,
 * and Skip. A capture goes straight to the form, with no confirm step. The Meal screen asks
 * for the camera; without it, gallery and Skip still work.
 */
@Composable
fun CameraStage(
    hasCamera: Boolean,
    newCaptureFile: () -> Pair<String, File>,
    onCaptured: (name: String) -> Unit,
    onPickFromGallery: () -> Unit,
    onSkip: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    LightSystemBarIcons()

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setResolutionSelector(CaptureResolution)
            .build()
    }
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    var capturing by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    if (hasCamera) {
        LaunchedEffect(lifecycleOwner) {
            val provider = ProcessCameraProvider.awaitInstance(context)
            val preview = Preview.Builder().setResolutionSelector(CaptureResolution).build()
            preview.setSurfaceProvider { surfaceRequest = it }
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            try {
                awaitCancellation()
            } finally {
                provider.unbind(preview, imageCapture)
                surfaceRequest = null
            }
        }
    }

    fun capture() {
        if (capturing || !hasCamera) return
        capturing = true
        val (name, file) = newCaptureFile()
        imageCapture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            context.mainExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    capturing = false
                    onCaptured(name)
                }

                override fun onError(exception: ImageCaptureException) {
                    capturing = false
                    file.delete()
                }
            },
        )
    }

    Column(
        modifier
            .fillMaxSize()
            .background(CameraBackground)
            .safeDrawingPadding(),
    ) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(FoodJournalIcons.Close, contentDescription = stringResource(R.string.close), tint = CameraInk)
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .background(Color(0xFF141414)),
            contentAlignment = Alignment.Center,
        ) {
            val request = surfaceRequest
            when {
                request != null -> CameraXViewfinder(surfaceRequest = request, modifier = Modifier.fillMaxSize())
                !hasCamera -> CameraOff()
            }
        }
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Equal-width sides keep the shutter exactly centered.
            Box(Modifier.width(SideControlWidth), contentAlignment = Alignment.Center) {
                IconButton(
                    onClick = onPickFromGallery,
                    modifier = Modifier
                        .size(52.dp)
                        .border(1.dp, CameraGrey, CircleShape),
                ) {
                    Icon(FoodJournalIcons.Gallery, contentDescription = stringResource(R.string.camera_gallery), tint = CameraInk)
                }
            }
            Shutter(enabled = hasCamera && surfaceRequest != null && !capturing, onClick = ::capture)
            Box(Modifier.width(SideControlWidth), contentAlignment = Alignment.Center) {
                TextButton(onClick = onSkip, modifier = Modifier.height(52.dp)) {
                    Text(stringResource(R.string.camera_skip), color = CameraInk)
                }
            }
        }
    }
}

@Composable
private fun Shutter(enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(76.dp)
            .border(3.dp, CameraInk, CircleShape)
            .padding(7.dp)
            .clip(CircleShape)
            .background(if (enabled) CameraInk else CameraGrey)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = stringResource(R.string.camera_shutter),
                interactionSource = null,
                indication = ripple(color = Color.Black),
                onClick = onClick,
            ),
    )
}

/** Camera permission was refused: one line and one button, as for any empty state. */
@Composable
private fun CameraOff() {
    val context = LocalContext.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
        Text(
            text = stringResource(R.string.camera_off),
            color = CameraGrey,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        OutlinedButton(onClick = {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
            )
        }) {
            Text(stringResource(R.string.camera_open_settings), color = CameraInk)
        }
    }
}

/** White status and navigation bar icons over the black camera, restored when it closes. */
@Composable
private fun LightSystemBarIcons() {
    val controller = LocalActivity.current?.window?.insetsController
    val darkTheme = isSystemInDarkTheme()
    val lightBars = APPEARANCE_LIGHT_STATUS_BARS or APPEARANCE_LIGHT_NAVIGATION_BARS
    DisposableEffect(controller, darkTheme) {
        controller?.setSystemBarsAppearance(0, lightBars)
        onDispose { if (!darkTheme) controller?.setSystemBarsAppearance(lightBars, lightBars) }
    }
}

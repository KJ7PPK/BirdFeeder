package com.kj7ppk.birdfeeder

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kj7ppk.birdfeeder.ui.MainViewModel
import com.kj7ppk.birdfeeder.ui.components.AudioVisualizer
import com.kj7ppk.birdfeeder.ui.components.ControlsSection
import com.kj7ppk.birdfeeder.ui.theme.BirdFeederTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BirdFeederTheme {
                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val isStreaming by viewModel.isStreaming.collectAsState()
    val audioLevels by viewModel.audioLevels.collectAsState()
    val rtspUrl by viewModel.rtspUrl.collectAsState()
    val gain by viewModel.gain.collectAsState()
    val selectedMic by viewModel.selectedMic.collectAsState()
    val availableMics by viewModel.availableMics.collectAsState()
    val selectedAudioSource by viewModel.selectedAudioSource.collectAsState()
    val selectedAudioCodec by viewModel.selectedAudioCodec.collectAsState()
    val isHomeLauncher by viewModel.isHomeLauncher.collectAsState()
    val isBatteryExempt by viewModel.isBatteryExempt.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.updateSystemSettingsStatus(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        if (permissions.values.all { it }) {
            viewModel.updateAvailableMics()
        }
    }

    LaunchedEffect(Unit) {
        val permissionsList = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsList.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val permissions = permissionsList.toTypedArray()
        
        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            // Background Leaf Pattern Decoration
            Canvas(modifier = Modifier.fillMaxSize()) {
                val leafColor = Color(0xFF2D5A27).copy(alpha = 0.05f)
                for (i in 0..8) {
                    val x = size.width * ((i * 0.15f) % 1f)
                    val y = size.height * ((i * 0.25f) % 1f)
                    drawPath(
                        path = Path().apply {
                            moveTo(x, y)
                            quadraticTo(x + 40f, y - 40f, x + 80f, y)
                            quadraticTo(x + 40f, y + 40f, x, y)
                            close()
                        },
                        color = leafColor,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Header with Bird Motif (Compact)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_bird_logo),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(56.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "BirdFeeder",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }

                // Combined Waveform, Status & RTSP URL Section
                CombinedStatusWaveformBox(
                    isActive = isStreaming,
                    url = rtspUrl,
                    audioLevels = audioLevels,
                )

                // Controls & Appliance Configuration Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        ControlsSection(
                            isStreaming = isStreaming,
                            onStreamingChange = { viewModel.toggleStreaming(it, context) },
                            selectedAudioSource = selectedAudioSource,
                            onAudioSourceChange = { viewModel.setSelectedAudioSource(it) },
                            selectedAudioCodec = selectedAudioCodec,
                            onAudioCodecChange = { viewModel.setSelectedAudioCodec(it) },
                            micSource = selectedMic,
                            availableMics = availableMics,
                            onMicChange = { viewModel.setSelectedMic(it) },
                            gain = gain,
                            onGainChange = { viewModel.setGain(it) },
                            isHomeLauncher = isHomeLauncher,
                            onOpenHomeSettings = { viewModel.openHomeSettings(context) },
                            isBatteryExempt = isBatteryExempt,
                            onRequestBatteryExemption = { viewModel.requestBatteryOptimizationExemption(context) },
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Footer Credits with GitHub Links
                FooterCredits()
            }
        }
    }
}

@Composable
fun CombinedStatusWaveformBox(
    isActive: Boolean,
    url: String,
    audioLevels: List<Float>,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = if (isActive) Color(0xFFE8F5E9) else Color(0xFFFAFAFA),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (isActive) Color(0xFFA5D6A7) else Color(0xFFEEEEEE)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(82.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Live Waveform background when streaming is ACTIVE
            if (isActive && audioLevels.isNotEmpty()) {
                AudioVisualizer(
                    levels = audioLevels,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 4.dp, horizontal = 8.dp),
                    barColor = Color(0xFF81C784).copy(alpha = 0.45f),
                )
            }

            // Status Indicator & RTSP URL Text Overlay
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 10.dp, horizontal = 8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                color = if (isActive) Color(0xFF4CAF50) else Color.LightGray,
                                shape = CircleShape,
                            ),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isActive) "STREAMING ACTIVE" else "STREAMING INACTIVE",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isActive) Color(0xFF2E7D32) else Color.Gray,
                        ),
                    )
                }
                if (isActive && url.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    SelectionContainer {
                        Text(
                            text = url,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FooterCredits() {
    val uriHandler = LocalUriHandler.current
    val annotatedString = buildAnnotatedString {
        append("BirdFeeder v1.2, built for ")

        pushStringAnnotation(tag = "URL", annotation = "https://github.com/tphakala/birdnet-go")
        withStyle(
            style = SpanStyle(
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                textDecoration = TextDecoration.Underline,
            ),
        ) {
            append("BirdNET-Go")
        }
        pop()

        append(".")
    }

    @Suppress("DEPRECATION")
    ClickableText(
        text = annotatedString,
        style = MaterialTheme.typography.bodySmall.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        onClick = { offset ->
            annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    try {
                        uriHandler.openUri(annotation.item)
                    } catch (_: Exception) {}
                }
        },
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp,dpi=440")
@Composable
fun MainScreenPreview() {
    BirdFeederTheme {
        MainScreen()
    }
}

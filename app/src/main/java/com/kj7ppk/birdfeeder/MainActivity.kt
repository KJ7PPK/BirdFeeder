package com.kj7ppk.birdfeeder

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlutterDash
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kj7ppk.birdfeeder.ui.MainViewModel
import com.kj7ppk.birdfeeder.ui.components.AudioVisualizer
import com.kj7ppk.birdfeeder.ui.components.ControlsSection
import com.kj7ppk.birdfeeder.ui.components.RtspUrlSection
import androidx.compose.ui.tooling.preview.Preview
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
    val port by viewModel.port.collectAsState()
    val selectedMic by viewModel.selectedMic.collectAsState()
    val availableMics by viewModel.availableMics.collectAsState()
    val audioSourceMode by viewModel.audioSourceMode.collectAsState()
    val foundMicIds by viewModel.foundMicIds.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            viewModel.updateAvailableMics()
        }
    }

    LaunchedEffect(Unit) {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS
        )
        
        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
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
                        color = leafColor
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with Bird Motif
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.FlutterDash,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "BirdFeeder",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Live View Section (Waveform)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        AudioVisualizer(
                            levels = audioLevels,
                            modifier = Modifier.padding(12.dp)
                        )
                        
                        // Decorative leaf in corner of waveform
                        Icon(
                            imageVector = Icons.Filled.FlutterDash,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).size(40.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Status Indicator
                StreamingStatusBox(isStreaming)

                Spacer(modifier = Modifier.height(16.dp))

                // RTSP URL Section
                if (isStreaming) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                    ) {
                        RtspUrlSection(
                            url = rtspUrl,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Controls & Engine Info Section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        ControlsSection(
                            isStreaming = isStreaming,
                            onStreamingChange = { viewModel.toggleStreaming(it) },
                            micSource = selectedMic,
                            availableMics = availableMics,
                            onMicChange = { viewModel.setSelectedMic(it) },
                            port = port,
                            onPortChange = { viewModel.setPort(it) },
                            gain = gain,
                            onGainChange = { viewModel.setGain(it) },
                            audioSourceMode = audioSourceMode,
                            foundMicIds = foundMicIds
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp,dpi=440")
@Composable
fun MainScreenPreview() {
    BirdFeederTheme {
        MainScreen()
    }
}

@Composable
fun StreamingStatusBox(isActive: Boolean) {
    Surface(
        color = if (isActive) Color(0xFFE8F5E9) else Color(0xFFFAFAFA),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (isActive) Color(0xFFC8E6C9) else Color(0xFFEEEEEE)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(
                        color = if (isActive) Color(0xFF4CAF50) else Color.LightGray,
                        shape = CircleShape
                    )
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = if (isActive) "STREAMING ACTIVE" else "STREAMING INACTIVE",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) Color(0xFF2E7D32) else Color.Gray
                )
            )
        }
    }
}

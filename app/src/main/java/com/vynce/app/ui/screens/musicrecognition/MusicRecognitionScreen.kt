package com.vynce.app.ui.screens.musicrecognition

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.darkxvenom.airbeats.shazamkit.Shazam
import com.darkxvenom.airbeats.shazamkit.ShazamSignatureGenerator
import com.darkxvenom.airbeats.shazamkit.models.RecognitionResult
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.vynce.app.LocalPlayerAwareWindowInsets
import com.vynce.app.data.spotify.SpotifyResolver
import com.vynce.app.playback.PlayerConnection
import com.vynce.app.playback.queues.ListQueue
import com.vynce.app.ui.theme.VyncePurple
import com.vynce.app.utils.toSaavnMediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class RecognitionState {
    object Idle : RecognitionState()
    object Listening : RecognitionState()
    object Processing : RecognitionState()
    data class Success(val result: RecognitionResult) : RecognitionState()
    data class NoMatch(val message: String) : RecognitionState()
    data class Error(val message: String) : RecognitionState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicRecognitionScreen(
    navController: NavController,
    playerConnection: PlayerConnection?
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var state by remember { mutableStateOf<RecognitionState>(RecognitionState.Listening) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) {
            startRecognition(onState = { state = it })
        } else {
            state = RecognitionState.Idle
            Toast.makeText(context, "Microphone permission required to recognize music", Toast.LENGTH_SHORT).show()
        }
    }

    // Auto-start listening on open
    LaunchedEffect(Unit) {
        if (hasPermission) {
            startRecognition(onState = { state = it })
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Radar animations
    val infiniteTransition = rememberInfiniteTransition(label = "RadarPulse")
    val pulse1 by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 2.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Pulse1"
    )
    val alpha1 by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Alpha1"
    )
    val pulse2 by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, delayMillis = 600, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Pulse2"
    )
    val alpha2 by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, delayMillis = 600, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Alpha2"
    )
    val pulse3 by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, delayMillis = 1200, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Pulse3"
    )
    val alpha3 by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, delayMillis = 1200, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Alpha3"
    )

    // Center button breathing scale
    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Breathing"
    )

    val bottomInsets = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Music Radar", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("Acoustic song identifier", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .padding(bottom = bottomInsets)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF130424),
                            Color(0xFF07040D),
                            Color.Black
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Status Header
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                ) {
                    Surface(
                        color = VyncePurple.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(20.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, VyncePurple.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.GraphicEq, null, tint = VyncePurple, modifier = Modifier.size(16.dp))
                            Text(
                                text = when (state) {
                                    is RecognitionState.Listening -> "LISTENING..."
                                    is RecognitionState.Processing -> "ANALYZING..."
                                    is RecognitionState.Success -> "MATCH FOUND"
                                    is RecognitionState.NoMatch -> "NO MATCH"
                                    is RecognitionState.Error -> "ERROR"
                                    is RecognitionState.Idle -> "READY TO IDENTIFY"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = VyncePurple,
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = when (state) {
                            is RecognitionState.Listening -> "Listening to nearby music"
                            is RecognitionState.Processing -> "Identifying acoustic fingerprint"
                            is RecognitionState.Success -> "Song Identified"
                            is RecognitionState.NoMatch -> "Couldn't identify track"
                            is RecognitionState.Error -> "Recognition Error"
                            is RecognitionState.Idle -> "Identify Any Playing Song"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(6.dp))

                    Text(
                        text = when (state) {
                            is RecognitionState.Listening -> "Hold your phone close to the audio source"
                            is RecognitionState.Processing -> "Searching high-fidelity music catalog..."
                            is RecognitionState.Idle -> "Tap the radar to identify songs playing nearby"
                            is RecognitionState.NoMatch -> "Try moving closer to the speaker or playing louder"
                            is RecognitionState.Error -> "Please check your network and try again"
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                }

                // Center Animated Radar or Result Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    when (val current = state) {
                        is RecognitionState.Idle, is RecognitionState.Listening, is RecognitionState.Processing -> {
                            Box(
                                modifier = Modifier.size(300.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (current is RecognitionState.Listening || current is RecognitionState.Processing) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val centerOffset = Offset(size.width / 2f, size.height / 2f)
                                        val baseRadius = size.minDimension / 4f

                                        // Outer Wave 1
                                        drawCircle(
                                            color = Color(0xFF9333EA).copy(alpha = alpha1),
                                            radius = baseRadius * pulse1,
                                            center = centerOffset,
                                            style = Stroke(width = 3.dp.toPx())
                                        )
                                        // Mid Wave 2
                                        drawCircle(
                                            color = Color(0xFF6366F1).copy(alpha = alpha2),
                                            radius = baseRadius * pulse2,
                                            center = centerOffset,
                                            style = Stroke(width = 2.5f.dp.toPx())
                                        )
                                        // Inner Wave 3
                                        drawCircle(
                                            color = Color(0xFF38BDF8).copy(alpha = alpha3),
                                            radius = baseRadius * pulse3,
                                            center = centerOffset,
                                            style = Stroke(width = 2.dp.toPx())
                                        )
                                    }
                                }

                                // Interactive Glowing Orb
                                val scale = if (current is RecognitionState.Listening) breathingScale else 1f
                                Surface(
                                    shape = CircleShape,
                                    color = Color.Transparent,
                                    modifier = Modifier
                                        .size(150.dp)
                                        .scale(scale)
                                        .shadow(
                                            elevation = if (current is RecognitionState.Listening) 24.dp else 8.dp,
                                            shape = CircleShape,
                                            ambientColor = VyncePurple,
                                            spotColor = VyncePurple
                                        )
                                        .background(
                                            Brush.radialGradient(
                                                listOf(
                                                    Color(0xFF9333EA),
                                                    Color(0xFF4C1D95),
                                                    Color(0xFF1E0836)
                                                )
                                            ),
                                            shape = CircleShape
                                        )
                                        .border(2.dp, Brush.linearGradient(listOf(Color(0xFFC084FC), Color(0xFF6366F1))), CircleShape)
                                        .clickable {
                                            if (hasPermission) {
                                                startRecognition(onState = { state = it })
                                            } else {
                                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            }
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        if (current is RecognitionState.Processing) {
                                            CircularProgressIndicator(
                                                color = Color.White,
                                                modifier = Modifier.size(52.dp),
                                                strokeWidth = 3.dp
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Rounded.GraphicEq,
                                                contentDescription = "Radar",
                                                tint = Color.White,
                                                modifier = Modifier.size(60.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        is RecognitionState.Success -> {
                            val result = current.result
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp),
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, VyncePurple.copy(alpha = 0.4f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    AsyncImage(
                                        model = result.coverArtHqUrl ?: result.coverArtUrl,
                                        contentDescription = result.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(170.dp)
                                            .clip(RoundedCornerShape(18.dp))
                                            .shadow(12.dp, RoundedCornerShape(18.dp))
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    Text(
                                        text = result.title,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = result.artist,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = VyncePurple,
                                        fontWeight = FontWeight.SemiBold,
                                        textAlign = TextAlign.Center
                                    )
                                    if (result.album?.isNotEmpty() == true) {
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = result.album ?: "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                    Spacer(Modifier.height(24.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                coroutineScope.launch {
                                                    Toast.makeText(context, "Loading 320kbps lossless stream...", Toast.LENGTH_SHORT).show()
                                                    val song = SpotifyResolver.resolveToSaavn(
                                                        trackName = result.title,
                                                        artistName = result.artist
                                                    )
                                                    if (song != null && playerConnection != null) {
                                                        val meta = song.toSaavnMediaMetadata()
                                                        playerConnection.playQueue(
                                                            ListQueue(
                                                                title = result.title,
                                                                items = listOf(meta),
                                                                startIndex = 0
                                                            )
                                                        )
                                                    } else {
                                                        navController.navigate("search?query=${result.title} ${result.artist}")
                                                    }
                                                }
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = VyncePurple)
                                        ) {
                                            Icon(Icons.Rounded.PlayArrow, null)
                                            Spacer(Modifier.width(4.dp))
                                            Text("Play Now")
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                navController.navigate("search?query=${result.title} ${result.artist}")
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Rounded.Search, null)
                                            Spacer(Modifier.width(4.dp))
                                            Text("Search")
                                        }
                                    }
                                }
                            }
                        }

                        is RecognitionState.NoMatch -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(20.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(90.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Rounded.Refresh,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(40.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    current.message,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Make sure the song is playing clearly without heavy background noise.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(24.dp))
                                Button(
                                    onClick = { startRecognition(onState = { state = it }) },
                                    colors = ButtonDefaults.buttonColors(containerColor = VyncePurple)
                                ) {
                                    Icon(Icons.Rounded.Refresh, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Try Again")
                                }
                            }
                        }

                        is RecognitionState.Error -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(20.dp)
                            ) {
                                Text(current.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.height(16.dp))
                                Button(
                                    onClick = { startRecognition(onState = { state = it }) },
                                    colors = ButtonDefaults.buttonColors(containerColor = VyncePurple)
                                ) {
                                    Icon(Icons.Rounded.Refresh, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Retry")
                                }
                            }
                        }
                    }
                }

                // Bottom Action Button
                if (state is RecognitionState.Success || state is RecognitionState.NoMatch || state is RecognitionState.Error) {
                    FilledTonalButton(
                        onClick = {
                            startRecognition(onState = { state = it })
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Icon(Icons.Rounded.GraphicEq, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Identify Another Song")
                    }
                } else {
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

private fun startRecognition(onState: (RecognitionState) -> Unit) {
    onState(RecognitionState.Listening)
    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
        try {
            val samples = recordMicPcm16Mono(sampleRateHz = 16000, recordMs = 4500L)
            onState(RecognitionState.Processing)

            val signature = ShazamSignatureGenerator().apply {
                feedPcm16Mono(samples)
            }.nextSignatureOrNull()

            if (signature == null) {
                onState(RecognitionState.Error("Could not generate audio signature"))
                return@launch
            }

            val result = Shazam.recognize(signature.uri, signature.sampleDurationMs)
            result.fold(
                onSuccess = { onState(RecognitionState.Success(it)) },
                onFailure = { onState(RecognitionState.NoMatch("No matching song found")) }
            )
        } catch (e: Exception) {
            onState(RecognitionState.Error(e.message ?: "Recognition failed"))
        }
    }
}

@Suppress("MissingPermission")
private suspend fun recordMicPcm16Mono(sampleRateHz: Int, recordMs: Long): ShortArray = withContext(Dispatchers.IO) {
    val channel = AudioFormat.CHANNEL_IN_MONO
    val encoding = AudioFormat.ENCODING_PCM_16BIT
    val minBuffer = AudioRecord.getMinBufferSize(sampleRateHz, channel, encoding).coerceAtLeast(4096)

    val audioRecord = AudioRecord(
        MediaRecorder.AudioSource.MIC,
        sampleRateHz,
        channel,
        encoding,
        minBuffer
    )

    val totalSamplesNeeded = (sampleRateHz * (recordMs / 1000.0)).toInt()
    val fullPcm = ShortArray(totalSamplesNeeded)
    var samplesRead = 0

    try {
        audioRecord.startRecording()
        val tempBuffer = ShortArray(1024)
        while (samplesRead < totalSamplesNeeded) {
            val read = audioRecord.read(tempBuffer, 0, tempBuffer.size.coerceAtMost(totalSamplesNeeded - samplesRead))
            if (read > 0) {
                System.arraycopy(tempBuffer, 0, fullPcm, samplesRead, read)
                samplesRead += read
            } else {
                break
            }
        }
    } finally {
        try {
            audioRecord.stop()
            audioRecord.release()
        } catch (_: Exception) {}
    }

    fullPcm
}

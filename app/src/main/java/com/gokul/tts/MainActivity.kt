package com.gokul.tts

import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gokul.tts.engine.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                GokulTtsScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GokulTtsScreen() {
    val context = LocalContext.current
    val prefs = remember { GokulApp.instance.prefs }
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedVoiceId by remember { mutableStateOf(prefs.voiceId) }
    var rate by remember { mutableFloatStateOf(prefs.rateMultiplier) }
    var pitch by remember { mutableFloatStateOf(prefs.pitchMultiplier) }
    var volume by remember { mutableFloatStateOf(prefs.volumeBoost) }
    var sampleText by remember { mutableStateOf("Hello! मी गोकुळ टीटीएस बोलत आहे.") }
    var isSpeaking by remember { mutableStateOf(false) }
    var punctMode by remember { mutableStateOf(prefs.punctuationMode) }
    var numMode by remember { mutableStateOf(prefs.numberMode) }
    var readEmoji by remember { mutableStateOf(prefs.readEmoji) }

    val isDefaultActive = remember {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.TTS_DEFAULT_SYNTH) == context.packageName
    }

    fun playPreview(text: String) {
        scope.launch {
            isSpeaking = true
            withContext(Dispatchers.Default) {
                val synth = FormantSynth()
                synth.configure(
                    VoiceCatalog.byId(selectedVoiceId), rate, pitch,
                    prefs.headSize, prefs.breathiness, prefs.roughness, volume
                )
                val norm = TextNormalizer.normalize(text, punctMode, numMode, prefs.capMode, readEmoji, "en-IN")
                synth.setPlan(Phonetizer.phonetize(norm))

                val minBuf = AudioTrack.getMinBufferSize(
                    AudioSpec.SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
                )
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(AudioSpec.SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBuf * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                track.play()
                val buf = ShortArray(512)
                while (synth.hasMore() && isSpeaking) {
                    val n = synth.generate(buf, 512)
                    if (n > 0) track.write(buf, 0, n)
                }
                track.stop()
                track.release()
            }
            isSpeaking = false
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Gokul TTS", fontWeight = FontWeight.Bold) }) },
        bottomBar = {
            NavigationBar {
                listOf("Dashboard", "Voices", "Settings").forEachIndexed { index, title ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Text(title.take(1), fontWeight = FontWeight.Bold) },
                        label = { Text(title) }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isDefaultActive) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        if (isDefaultActive) "Active: Gokul TTS is System Default"
                        else "Setup Required: Select Gokul TTS in Android Settings",
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { context.startActivity(Intent("com.android.settings.TTS_SETTINGS")) },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                    ) { Text("Open TTS Output Settings") }
                }
            }

            when (selectedTab) {
                0 -> {
                    OutlinedTextField(
                        value = sampleText,
                        onValueChange = { sampleText = it },
                        label = { Text("Test Speech Text") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { if (isSpeaking) isSpeaking = false else playPreview(sampleText) },
                            modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp)
                        ) { Text(if (isSpeaking) "Stop" else "Speak Preview") }
                        OutlinedButton(
                            onClick = { sampleText = "" },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        ) { Text("Clear") }
                    }
                    Text("Speech Rate: ${String.format("%.1f", rate)}x (${(rate * 160).toInt()} WPM)")
                    Slider(
                        value = rate,
                        onValueChange = { rate = it; prefs.rateMultiplier = it },
                        valueRange = 0.5f..6.0f,
                        modifier = Modifier.semantics { contentDescription = "Speech Rate Slider" }
                    )
                    Text("Pitch: ${(pitch * 100).toInt()}%")
                    Slider(
                        value = pitch,
                        onValueChange = { pitch = it; prefs.pitchMultiplier = it },
                        valueRange = 0.5f..2.0f,
                        modifier = Modifier.semantics { contentDescription = "Pitch Slider" }
                    )
                    Text("Volume Boost: ${(volume * 100).toInt()}%")
                    Slider(
                        value = volume,
                        onValueChange = { volume = it; prefs.volumeBoost = it },
                        valueRange = 0.5f..2.0f,
                        modifier = Modifier.semantics { contentDescription = "Volume Boost Slider" }
                    )
                }
                1 -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(VoiceCatalog.ALL) { voice ->
                            val selected = voice.id == selectedVoiceId
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    selectedVoiceId = voice.id
                                    prefs.voiceId = voice.id
                                    playPreview("Hello, I am ${voice.displayName}.")
                                },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Text("${voice.displayName} ${if (selected) "✓" else ""}", fontWeight = FontWeight.Bold)
                                    Text(voice.description, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                2 -> {
                    Text("Punctuation Verbosity", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PunctuationMode.entries.forEach { mode ->
                            FilterChip(
                                selected = punctMode == mode,
                                onClick = { punctMode = mode; prefs.punctuationMode = mode },
                                label = { Text(mode.name) }
                            )
                        }
                    }
                    Text("Number Reading Mode", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberMode.entries.forEach { mode ->
                            FilterChip(
                                selected = numMode == mode,
                                onClick = { numMode = mode; prefs.numberMode = mode },
                                label = { Text(mode.name) }
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Read Emojis Aloud", modifier = Modifier.weight(1f))
                        Switch(checked = readEmoji, onCheckedChange = { readEmoji = it; prefs.readEmoji = it })
                    }
                }
            }
        }
    }
}


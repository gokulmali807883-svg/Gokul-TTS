package com.gokul.tts.service

import android.app.Activity
import android.content.Intent
import android.media.AudioFormat
import android.os.Bundle
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import com.gokul.tts.GokulApp
import com.gokul.tts.Phonetizer
import com.gokul.tts.engine.AudioSpec
import com.gokul.tts.engine.FormantSynth
import com.gokul.tts.engine.TextNormalizer
import com.gokul.tts.engine.VoiceCatalog
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

class GokulTtsService : TextToSpeechService() {

    private val synth = FormantSynth()
    @Volatile
    private var stopRequested = false
    private var currentLang = arrayOf("eng", "USA", "")
    private val supportedLocales = setOf("eng", "hin", "mar")

    override fun onIsLanguageAvailable(lang: String?, country: String?, variant: String?): Int {
        val l = lang?.lowercase(Locale.ROOT) ?: return TextToSpeech.LANG_NOT_SUPPORTED
        if (l !in supportedLocales) return TextToSpeech.LANG_NOT_SUPPORTED
        return if (!country.isNullOrEmpty()) TextToSpeech.LANG_COUNTRY_AVAILABLE else TextToSpeech.LANG_AVAILABLE
    }

    override fun onLoadLanguage(lang: String?, country: String?, variant: String?): Int {
        val status = onIsLanguageAvailable(lang, country, variant)
        if (status >= TextToSpeech.LANG_AVAILABLE && lang != null) {
            currentLang = arrayOf(lang, country ?: "", variant ?: "")
        }
        return status
    }

    override fun onGetLanguage(): Array<String> = currentLang

    override fun onStop() {
        stopRequested = true
        synth.reset()
    }

    override fun onSynthesizeText(request: SynthesisRequest?, callback: SynthesisCallback?) {
        if (request == null || callback == null) return
        stopRequested = false

        val text = request.charSequenceText?.toString().orEmpty()
        if (text.isBlank()) {
            callback.start(AudioSpec.SAMPLE_RATE, AudioFormat.ENCODING_PCM_16BIT, 1)
            callback.done()
            return
        }

        val prefs = GokulApp.instance.prefs
        val langTag = "${request.language ?: "en"}-${request.country ?: "US"}"
        val selectedVoice = VoiceCatalog.byId(prefs.voiceId)
        val systemRate = (request.speechRate / 100f).coerceIn(0.5f, 6.0f)
        val effectiveRate = (systemRate * prefs.rateMultiplier).coerceIn(0.5f, 6.0f)
        val systemPitch = (request.pitch / 100f).coerceIn(0.5f, 2.0f)
        val effectivePitch = (systemPitch * prefs.pitchMultiplier).coerceIn(0.5f, 2.0f)

        synth.configure(
            voice = selectedVoice,
            rate = effectiveRate,
            pitch = effectivePitch,
            headSize = prefs.headSize,
            breathiness = prefs.breathiness,
            roughness = prefs.roughness,
            volume = prefs.volumeBoost
        )

        val normalized = TextNormalizer.normalize(
            text = text,
            punctuation = prefs.punctuationMode,
            numberMode = prefs.numberMode,
            capMode = prefs.capMode,
            readEmoji = prefs.readEmoji,
            langPrefix = langTag.lowercase(Locale.ROOT)
        )

        callback.start(AudioSpec.SAMPLE_RATE, AudioFormat.ENCODING_PCM_16BIT, 1)
        val chunkSize = 512
        val shortBuf = ShortArray(chunkSize)
        val byteBuf = ByteArray(chunkSize * 2)

        for (sentence in TextNormalizer.sentences(normalized)) {
            if (stopRequested) break
            synth.setPlan(Phonetizer.phonetize(sentence))
            while (synth.hasMore() && !stopRequested) {
                val count = synth.generate(shortBuf, chunkSize)
                if (count <= 0) break
                ByteBuffer.wrap(byteBuf, 0, count * 2)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .asShortBuffer()
                    .put(shortBuf, 0, count)
                if (callback.audioAvailable(byteBuf, 0, count * 2) == TextToSpeech.ERROR) {
                    stopRequested = true
                    break
                }
            }
        }
        if (!stopRequested) callback.done()
    }
}

class CheckVoiceDataActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val result = Intent().apply {
            putStringArrayListExtra(
                TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES,
                arrayListOf("eng-USA", "eng-GBR", "eng-IND", "hin-IND", "mar-IND")
            )
            putStringArrayListExtra(TextToSpeech.Engine.EXTRA_UNAVAILABLE_VOICES, arrayListOf())
        }
        setResult(TextToSpeech.Engine.CHECK_VOICE_DATA_PASS, result)
        finish()
    }
}

class GetSampleTextActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val lang = intent?.getStringExtra("language")?.lowercase() ?: "eng"
        val sample = when {
            lang.startsWith("mar") || lang.startsWith("mr") -> "नमस्कार, मी गोकुळ टीटीएस आहे."
            lang.startsWith("hin") || lang.startsWith("hi") -> "नमस्ते, मैं गोकुल टीटीएस हूँ।"
            else -> "Hello, this is Gokul TTS for TalkBack."
        }
        setResult(TextToSpeech.Engine.LANG_AVAILABLE, Intent().putExtra(TextToSpeech.Engine.EXTRA_SAMPLE_TEXT, sample))
        finish()
    }
}

class InstallVoiceDataActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_OK)
        finish()
    }
}


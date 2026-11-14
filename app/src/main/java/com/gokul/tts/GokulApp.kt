package com.gokul.tts

import android.app.Application
import android.content.Context
import com.gokul.tts.engine.*

class GokulApp : Application() {
    lateinit var prefs: TtsPrefs
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = TtsPrefs(this)
    }

    companion object {
        lateinit var instance: GokulApp
            private set
    }
}

class TtsPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("gokul_tts_prefs", Context.MODE_PRIVATE)

    var voiceId: String
        get() = prefs.getString("voice_id", VoiceCatalog.REED.id) ?: VoiceCatalog.REED.id
        set(value) = prefs.edit().putString("voice_id", value).apply()

    var rateMultiplier: Float
        get() = prefs.getFloat("rate_mul", 1.0f)
        set(value) = prefs.edit().putFloat("rate_mul", value).apply()

    var pitchMultiplier: Float
        get() = prefs.getFloat("pitch_mul", 1.0f)
        set(value) = prefs.edit().putFloat("pitch_mul", value).apply()

    var headSize: Float
        get() = prefs.getFloat("head_size", 1.0f)
        set(value) = prefs.edit().putFloat("head_size", value).apply()

    var breathiness: Float
        get() = prefs.getFloat("breathiness", 1.0f)
        set(value) = prefs.edit().putFloat("breathiness", value).apply()

    var roughness: Float
        get() = prefs.getFloat("roughness", 1.0f)
        set(value) = prefs.edit().putFloat("roughness", value).apply()

    var volumeBoost: Float
        get() = prefs.getFloat("volume_boost", 1.0f)
        set(value) = prefs.edit().putFloat("volume_boost", value).apply()

    var punctuationMode: PunctuationMode
        get() = PunctuationMode.entries.getOrElse(prefs.getInt("punct_mode", 2)) { PunctuationMode.SOME }
        set(value) = prefs.edit().putInt("punct_mode", value.ordinal).apply()

    var numberMode: NumberMode
        get() = NumberMode.entries.getOrElse(prefs.getInt("number_mode", 0)) { NumberMode.WORDS }
        set(value) = prefs.edit().putInt("number_mode", value.ordinal).apply()

    var capMode: CapMode
        get() = CapMode.entries.getOrElse(prefs.getInt("cap_mode", 0)) { CapMode.NONE }
        set(value) = prefs.edit().putInt("cap_mode", value.ordinal).apply()

    var readEmoji: Boolean
        get() = prefs.getBoolean("read_emoji", true)
        set(value) = prefs.edit().putBoolean("read_emoji", value).apply()
}

object Phonetizer {
    private val DEVANAGARI_VOWELS = mapOf(
        'अ' to listOf("AH"), 'आ' to listOf("AA_M"), 'इ' to listOf("IH"), 'ई' to listOf("II_M"),
        'उ' to listOf("UH"), 'ऊ' to listOf("UU_M"), 'ए' to listOf("E_M"), 'ऐ' to listOf("AI_M"),
        'ओ' to listOf("O_M"), 'औ' to listOf("AU_M"), 'ऋ' to listOf("R", "IH")
    )
    private val DEVANAGARI_MATRAS = mapOf(
        'ा' to "AA_M", 'ि' to "IH", 'ी' to "II_M", 'ु' to "UH", 'ू' to "UU_M",
        'े' to "E_M", 'ै' to "AI_M", 'ो' to "O_M", 'ौ' to "AU_M", 'ृ' to "R",
        'ं' to "ANNUN", 'ः' to "HH"
    )
    private val DEVANAGARI_CONSONANTS = mapOf(
        'क' to listOf("K"), 'ख' to listOf("K", "HH"), 'ग' to listOf("G"), 'घ' to listOf("G", "HH"),
        'च' to listOf("CH"), 'छ' to listOf("CH", "HH"), 'ज' to listOf("JH"), 'झ' to listOf("JH", "HH"),
        'ट' to listOf("TT"), 'ठ' to listOf("TT", "HH"), 'ड' to listOf("DD"), 'ढ' to listOf("DD", "HH"),
        'ण' to listOf("NN"), 'त' to listOf("T"), 'थ' to listOf("TH"), 'द' to listOf("D"),
        'ध' to listOf("DH"), 'न' to listOf("N"), 'प' to listOf("P"), 'फ' to listOf("F"),
        'ब' to listOf("B"), 'भ' to listOf("B", "HH"), 'म' to listOf("M"), 'य' to listOf("Y"),
        'र' to listOf("R"), 'ल' to listOf("L"), 'ळ' to listOf("L"), 'व' to listOf("V"),
        'श' to listOf("SH"), 'ष' to listOf("SH"), 'स' to listOf("S"), 'ह' to listOf("HH")
    )

    fun phonetize(text: String): List<Phoneme> {
        val result = mutableListOf<Phoneme>()
        val clean = text.replace("⟦cap⟧", "").trim()
        for (word in clean.split(Regex("\\s+"))) {
            if (word.isEmpty()) continue
            if (word.any { it in '\u0900'..'\u097F' }) phonetizeDevanagari(word, result)
            else phonetizeEnglish(word.lowercase(), result)
            result.add(PhonemeTable.SILENCE.copy(durMs = 45f))
        }
        return result
    }

    private fun phonetizeDevanagari(word: String, out: MutableList<Phoneme>) {
        var i = 0
        while (i < word.length) {
            val ch = word[i]
            when {
                ch in DEVANAGARI_VOWELS -> DEVANAGARI_VOWELS[ch]?.forEach { out.add(PhonemeTable.get(it)) }
                ch in DEVANAGARI_CONSONANTS -> {
                    DEVANAGARI_CONSONANTS[ch]?.forEach { out.add(PhonemeTable.get(it)) }
                    val next = word.getOrNull(i + 1)
                    if (next == '्') i++
                    else if (next != null && next in DEVANAGARI_MATRAS) {
                        DEVANAGARI_MATRAS[next]?.let { out.add(PhonemeTable.get(it)) }
                        i++
                    } else if (i < word.length - 1) out.add(PhonemeTable.get("AH"))
                }
                ch in DEVANAGARI_MATRAS -> DEVANAGARI_MATRAS[ch]?.let { out.add(PhonemeTable.get(it)) }
                ch in ".!?।" -> out.add(PhonemeTable.SILENCE.copy(durMs = 180f))
                ch in ",;:" -> out.add(PhonemeTable.SILENCE.copy(durMs = 110f))
            }
            i++
        }
    }

    private fun phonetizeEnglish(word: String, out: MutableList<Phoneme>) {
        var i = 0
        while (i < word.length) {
            val ch = word[i]
            val next = word.getOrNull(i + 1)
            if (next != null) {
                val digraph = when ("$ch$next") {
                    "th" -> "TH"; "sh" -> "SH"; "ch" -> "CH"; "ph" -> "F"; "ng" -> "NG"
                    "ee", "ea" -> "IY"; "oo" -> "UW"; "ai", "ay" -> "EY"; "oi", "oy" -> "OY"; "ou", "ow" -> "AW"
                    else -> null
                }
                if (digraph != null) { out.add(PhonemeTable.get(digraph)); i += 2; continue }
            }
            val id = when (ch) {
                'a' -> "AE"; 'b' -> "B"; 'c' -> if (next in "eiy") "S" else "K"; 'd' -> "D"
                'e' -> if (i == word.length - 1 && word.length > 2) null else "EH"; 'f' -> "F"
                'g' -> if (next in "eiy") "JH" else "G"; 'h' -> "HH"; 'i' -> "IH"; 'j' -> "JH"
                'k' -> "K"; 'l' -> "L"; 'm' -> "M"; 'n' -> "N"; 'o' -> "AA"; 'p' -> "P"; 'q' -> "K"
                'r' -> "R"; 's' -> "S"; 't' -> "T"; 'u' -> "AH"; 'v' -> "V"; 'w' -> "W"
                'x' -> { out.add(PhonemeTable.get("K")); "S" }; 'y' -> if (i == 0) "Y" else "IY"; 'z' -> "Z"
                '.', '!', '?' -> { out.add(PhonemeTable.SILENCE.copy(durMs = 180f)); null }
                ',', ';', ':' -> { out.add(PhonemeTable.SILENCE.copy(durMs = 110f)); null }
                else -> null
            }
            if (id != null) out.add(PhonemeTable.get(id))
            i++
        }
    }
}


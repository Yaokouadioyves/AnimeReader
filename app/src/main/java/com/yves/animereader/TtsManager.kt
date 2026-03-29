package com.yves.animereader

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class TtsManager(private val context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.FRENCH)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TtsManager", "Langue française non supportée ou données manquantes.")
            } else {
                isInitialized = true
                Log.i("TtsManager", "TTS initialisé avec succès en français.")
            }
        } else {
            Log.e("TtsManager", "Échec de l'initialisation du TTS.")
        }
    }

    fun speak(text: String) {
        if (isInitialized) {
            stop() // Arrêter la lecture précédente
            Log.d("TtsManager", "Lecture du texte : $text")
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "AnimeReaderTTS")
        } else {
            Log.e("TtsManager", "Le TTS n'est pas encore initialisé.")
        }
    }

    fun stop() {
        if (isInitialized && tts?.isSpeaking == true) {
            tts?.stop()
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}

package com.yves.animereader

import android.content.Context
import android.media.MediaPlayer
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class TtsManager(private val context: Context) {
    private val client = OkHttpClient()
    private var mediaPlayer: MediaPlayer? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    // Utilisation d'une clé API Google Cloud (permanente) au lieu d'un token OAuth
    private val API_KEY = "AIzaSyDaCuKBJnoOuPvok7X6-X-r-1uK4V95EVI"

    fun speak(text: String) {
        // Arrêter l'audio précédent si on lance une nouvelle bulle
        stop()

        coroutineScope.launch {
            try {
                // 1. Préparer la requête JSON pour Gemini 2.5 Flash TTS
                val jsonBody = JSONObject().apply {
                    put("input", JSONObject().apply {
                        put("prompt", "Lis le texte en français de manière très expressive et naturelle, comme si tu doublais un anime.")
                        put("text", text)
                    })
                    put("voice", JSONObject().apply {
                        put("languageCode", "fr-FR")
                        put("name", "Orus")
                        put("model_name", "gemini-2.5-flash-tts")
                    })
                    put("audioConfig", JSONObject().apply {
                        put("audioEncoding", "MP3")
                    })
                }

                val requestBody = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

                val request = Request.Builder()
                    .url("https://texttospeech.googleapis.com/v1/text:synthesize?key=$API_KEY")
                    .post(requestBody)
                    .build()

                Log.d("TtsManager", "Envoi de la requête à Google Cloud pour: $text")

                // 2. Exécuter la requête HTTP
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    val jsonResponse = JSONObject(responseBody!!)
                    val audioBase64 = jsonResponse.getString("audioContent")

                    // 3. Convertir le Base64 en fichier MP3 temporaire
                    val audioBytes = Base64.decode(audioBase64, Base64.DEFAULT)
                    val tempAudioFile = File(context.cacheDir, "temp_audio.mp3")
                    FileOutputStream(tempAudioFile).use { it.write(audioBytes) }

                    // 4. Jouer l'audio sur le Main Thread
                    withContext(Dispatchers.Main) {
                        playAudio(tempAudioFile.absolutePath)
                    }
                } else {
                    Log.e("TtsManager", "Erreur API: ${response.code} - ${response.body?.string()}")
                }
            } catch (e: Exception) {
                Log.e("TtsManager", "Erreur réseau: ${e.message}")
            }
        }
    }

    private fun playAudio(filePath: String) {
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(filePath)
                prepare()
                start()
                setOnCompletionListener {
                    // Libérer les ressources quand l'audio est terminé
                    it.release()
                    mediaPlayer = null
                }
            }
        } catch (e: Exception) {
            Log.e("TtsManager", "Erreur lors de la lecture audio: ${e.message}")
        }
    }
    
    fun stop() {
        try {
            mediaPlayer?.takeIf { it.isPlaying }?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            // Ignorer les erreurs d'arrêt
        }
    }

    fun shutdown() {
        stop()
    }
}

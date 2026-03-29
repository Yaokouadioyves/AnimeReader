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

    // TODO: Remplacez cette valeur par le résultat de la commande "gcloud auth print-access-token"
    // Note: C'est pour tester rapidement. Dans la version finale, nous intégrerons
    // votre fichier JSON directement dans l'application pour générer ce jeton automatiquement.
    private val ACCESS_TOKEN = "ya29.c.c0AZ4bNpbKV3KOe_Spy30_lL6c4uXgAVIVmrpDl3SmpzdPQpPiieVHSlx8sZUfMWtQqmfGAsCxCeU9_BDx2JUgA0I0R69Dh-QN1poqDPJldaYTu5ksUTi4fXVIZQIgSqfEVd_WEZtFl4lwaqioKeZ7OJmCf9sOXB6h6JBluP6SzlJVVcorcUhU--yJYpZYyMPYZXnvkgx1tP2u-CMvoCpPu1WHF94tzmCGs11NTgQl5ZVPCL7cgVDlLBHw2RnUT457ElONPxtFBMQBJPKEG94vJLSv417kcefNM6A7luL3Eups-AXRWMxkKTkzBiq5eS4G_tV5FABE1y_ghE5FdLwEm0inQitSAk651qZKufx1SpCBpOUs6xMa8y58RyAcjeCdf9saXA8T400Kzla7YmvY1_Rz2_td39-lx6r4dlipRxtumopBRx0bck6w9r6RywuJJxzRm7Xdc30tsZvIOerxihOt1WFWSh_q3IjbQh6s39smIs_9ia3kkrptIk8dklhwqcOBnuQyr2FJM6qZdY5yprJwrad_Rc6ty6hxa_gz5o4VxX1IBqIWktVl1h-5dj7e1oF5wjJkOut6Qysjsig_cfmFwfmkIwWOtdRUxX02oFJemb-Vc63a8o4Wtfd6z1QQWb_l1lafb5peRMeJfkur7MfanfrJ1oIzl_c9-jr0qef3UQBMX7Vksby7wh7iM4y94kWieO0uM4JZSnXu8_6Xk9I2q8vQ84JqIOm6f6bR4WiiX6pWqF-cj1Qq0eWBB4hQBUMudcR6ccrb1ZV_ml0Y1yvwfMWvyXnt8bluQRp_h-p57BUx5gvc1jbynXcpOuR-925Zdfah1RmrqsO-7d6vmU1Zh5pU0Mwbzr4Jb5OZeXw5cm2O0stB8yR77md0ilYFcgdtUaX8X246umx_kx8hyYmRhi1doqex5dkFbkpUQFekBdq-nFXhj4u6V6M4I-as_zj2SVsw3t0dh7vUqhtc3MBi1bRowb0WB4vl3QJQq54fdt0ujvml_3M"
    private val PROJECT_ID = "gen-lang-client-0177317636"

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
                    .url("https://texttospeech.googleapis.com/v1/text:synthesize")
                    .addHeader("Authorization", "Bearer $ACCESS_TOKEN")
                    .addHeader("x-goog-user-project", PROJECT_ID)
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

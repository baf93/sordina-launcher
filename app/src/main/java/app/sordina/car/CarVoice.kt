package app.sordina.car

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.car.app.CarContext
import androidx.car.app.media.CarAudioRecord
import app.sordina.R
import app.sordina.tr
import java.util.Locale

/**
 * La voce di Sordina in auto. Ascolta una frase dal microfono dell'auto (Car API 5 e Android 13: se no quello del
 * telefono) e la riconosce sul telefono, come la barra di Casa: niente esce. Risponde con una frase corta, letta.
 */
object CarVoice {
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var record: CarAudioRecord? = null
    private var pump: Thread? = null
    @Volatile private var pumping = false
    private var focus: AudioFocusRequest? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pending: String? = null

    private val speech = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    /** Il microfono si può usare: se no si chiede, sul telefono */
    fun allowed(cc: CarContext) = cc.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /**
     * Ascolta una frase. [onHeard]: le trascrizioni, la più probabile per prima (vuota: non ha sentito niente).
     * [onError]: la frase da dire. Si chiama sul thread principale.
     */
    fun listen(cc: CarContext, onPartial: (String) -> Unit, onHeard: (List<String>) -> Unit, onError: (String) -> Unit) {
        cancel(cc)
        if (Build.VERSION.SDK_INT < 31 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(cc)) { onError(tr(R.string.voice_offline)); return }
        val am = cc.getSystemService(AudioManager::class.java)
        val f = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE).setAudioAttributes(speech).build()
        runCatching { am.requestAudioFocus(f) }
        focus = f
        val r = SpeechRecognizer.createOnDeviceSpeechRecognizer(cc).also { recognizer = it }
        var done = false
        fun finish(block: () -> Unit) { if (done) return; done = true; stopAudio(cc); main.post(block) }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            // Hai finito di parlare: il microfono dell'auto si chiude, il riconoscitore chiude la frase
            override fun onEndOfSpeech() { stopPump() }
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onPartialResults(partial: Bundle?) {
                partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }?.let(onPartial)
            }
            override fun onResults(results: Bundle?) {
                val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty().filter { it.isNotBlank() }
                finish { onHeard(heard) }
            }
            override fun onError(error: Int) {
                finish {
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> onHeard(emptyList())
                        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> onError(tr(R.string.voice_no_lang))
                        SpeechRecognizer.ERROR_CLIENT -> Unit
                        else -> onError(tr(R.string.mic_unavailable))
                    }
                }
            }
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale(cc).toLanguageTag())
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1_500L)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1_200L)
        // Il microfono dell'auto: la voce arriva da Android Auto e passa al riconoscitore in un tubo
        if (Build.VERSION.SDK_INT >= 33 && cc.carAppApiLevel >= 5) runCatching { carMic(cc, i) }.onFailure { stopAudio(cc) }
        runCatching { r.startListening(i) }.onFailure { finish { onError(tr(R.string.mic_unavailable)) } }
        // Se il riconoscitore non chiude da solo, dopo un po' basta
        main.postDelayed({ if (!done) runCatching { recognizer?.stopListening() } }, MAX_MILLIS)
    }

    @androidx.annotation.RequiresApi(33)
    private fun carMic(cc: CarContext, i: Intent) {
        if (cc.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        val rec = CarAudioRecord.create(cc)
        val (read, write) = ParcelFileDescriptor.createPipe()
        i.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, read)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, CarAudioRecord.AUDIO_CONTENT_SAMPLING_RATE)
        rec.startRecording()
        record = rec
        pumping = true
        pump = Thread {
            ParcelFileDescriptor.AutoCloseOutputStream(write).use { out ->
                val buf = ByteArray(CarAudioRecord.AUDIO_CONTENT_BUFFER_SIZE)
                while (pumping) {
                    val n = runCatching { rec.read(buf, 0, buf.size) }.getOrDefault(-1)
                    if (n <= 0) break
                    runCatching { out.write(buf, 0, n) }.onFailure { pumping = false }
                }
            }
            runCatching { rec.stopRecording() }
        }.apply { isDaemon = true; name = "sordina-car-mic"; start() }
    }

    private fun stopPump() { pumping = false }

    private fun stopAudio(cc: CarContext) {
        stopPump()
        record = null
        pump = null
        focus?.let { f -> runCatching { cc.getSystemService(AudioManager::class.java).abandonAudioFocusRequest(f) } }
        focus = null
    }

    /** Smette di ascoltare senza risposta */
    fun cancel(cc: CarContext) {
        main.removeCallbacksAndMessages(null)
        recognizer?.let { runCatching { it.cancel() }; runCatching { it.destroy() } }
        recognizer = null
        stopAudio(cc)
    }

    /** Dice una frase corta. La prima volta la voce si prepara: la frase aspetta */
    fun say(cc: CarContext, text: String) {
        val t = tts
        if (t == null) {
            pending = text
            tts = TextToSpeech(cc.applicationContext) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
                if (ttsReady) {
                    runCatching { tts?.language = locale(cc) }
                    runCatching { tts?.setAudioAttributes(speech) }
                    pending?.let { p -> pending = null; speakNow(p) }
                }
            }
            return
        }
        if (ttsReady) speakNow(text) else pending = text
    }

    private fun speakNow(text: String) { runCatching { tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "sordina") } }

    /** Scollegata l'auto: via microfono e voce */
    fun release() {
        main.removeCallbacksAndMessages(null)
        recognizer?.let { runCatching { it.destroy() } }
        recognizer = null
        pumping = false
        tts?.let { runCatching { it.stop() }; runCatching { it.shutdown() } }
        tts = null
        ttsReady = false
        pending = null
    }

    /** La lingua di Sordina (scelta nelle impostazioni, se no quella del telefono) */
    private fun locale(cc: CarContext): Locale = app.sordina.Lang.chosen(cc)?.let(Locale::forLanguageTag) ?: Locale.getDefault()

    private const val MAX_MILLIS = 10_000L
}

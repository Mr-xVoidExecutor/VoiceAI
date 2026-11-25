package com.voiceai.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val SYS = "তুমি একটি ভয়েস অ্যাসিস্ট্যান্ট, মেয়েলি কণ্ঠের। ব্যবহারকারী যে ভাষায় কথা বলে সেই ভাষায় ১-৩ ছোট বাক্যে উত্তর দাও। মার্কডাউন, তালিকা বা ইমোজি ব্যবহার করবে না। "

val PERSONAS = linkedMapOf(
    "বন্ধু" to "তুমি ব্যবহারকারীর আন্তরিক বন্ধু; হালকা মেজাজে কথা বলো।",
    "শিক্ষক" to "তুমি ধৈর্যশীল শিক্ষক; সহজ করে বুঝিয়ে বলো।",
    "ইংরেজি কোচ" to "তুমি ইংরেজি স্পোকেন কোচ; ইংরেজিতে কথা বলো, ভুল হলে বাংলায় সংক্ষেপে শুধরে দাও।",
    "মোটিভেটর" to "তুমি ইতিবাচক মোটিভেটর; উৎসাহ দাও কিন্তু বাস্তবসম্মত থাকো।"
)
val VOICES = listOf("Kore", "Aoede", "Leda", "Zephyr", "Callirrhoe")

enum class S(val label: String) {
    IDLE("মাইক চাপুন"), LISTENING("শুনছি…"), THINKING("ভাবছি…"), SPEAKING("বলছি…")
}

class Engine(private val a: ComponentActivity) {
    private val prefs = a.getSharedPreferences("p", 0)
    private val ui = Handler(Looper.getMainLooper())
    var state by mutableStateOf(S.IDLE)
    var error by mutableStateOf("")
    var note by mutableStateOf("")
    val log = mutableStateListOf<Pair<String, String>>()
    var apiKey by mutableStateOf(prefs.getString("key", "") ?: "")
    var model by mutableStateOf(prefs.getString("model3", "gemini-3.8-flash") ?: "gemini-3.8-flash")
    var ttsModel by mutableStateOf(prefs.getString("ttsm", "gemini-3.1-flash-tts-preview") ?: "gemini-3.1-flash-tts-preview")
    var voice by mutableStateOf(prefs.getString("voice", "Kore") ?: "Kore")
    var persona by mutableStateOf(prefs.getString("persona", "বন্ধু") ?: "বন্ধু")
    var lang by mutableStateOf(prefs.getString("lang", "bn-BD") ?: "bn-BD")
    var natural by mutableStateOf(prefs.getBoolean("nat", true))
    var rain by mutableStateOf(prefs.getBoolean("rain", true))
    private var active = false
    private var loop = false
    private var gen = 0
    @Volatile private var playing = false
    private var track: AudioTrack? = null
    private var sr: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    init {
        tts = TextToSpeech(a) { st ->
            ttsReady = st == TextToSpeech.SUCCESS
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) { ui.post { if (id == gen.toString()) afterSpeak() } }
                override fun onError(id: String?) { ui.post { if (id == gen.toString()) afterSpeak() } }
            })
        }
    }

    fun save() {
        prefs.edit().putString("key", apiKey.trim()).putString("model3", model.trim()).putString("ttsm", ttsModel.trim())
            .putString("voice", voice).putString("persona", persona).putString("lang", lang)
            .putBoolean("nat", natural).putBoolean("rain", rain).apply()
    }

    fun start() {
        if (apiKey.isBlank()) { error = "⚙ Settings থেকে API key দিন"; return }
        if (!SpeechRecognizer.isRecognitionAvailable(a)) { error = "এই ফোনে Speech recognition নেই"; return }
        active = true; loop = true; error = ""; listen()
    }

    fun sendText(t: String) {
        if (t.isBlank()) return
        if (apiKey.isBlank()) { error = "⚙ Settings থেকে API key দিন"; return }
        sr?.cancel(); stopSpeech(); active = true; loop = false; error = ""
        ask(t.trim())
    }

    private fun stopSpeech() {
        gen++; playing = false
        track?.let { runCatching { it.pause(); it.flush() } }
        tts?.stop()
    }

    fun stop() { active = false; loop = false; sr?.cancel(); stopSpeech(); state = S.IDLE }

    fun interrupt() {
        if (state != S.SPEAKING) return
        stopSpeech()
        if (active && loop) listen() else { active = false; state = S.IDLE }
    }

    fun release() { active = false; stopSpeech(); sr?.destroy(); tts?.shutdown() }

    private fun listen() {
        if (!active) return
        state = S.LISTENING
        sr?.destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(a)
        sr = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onResults(b: Bundle?) {
                val t = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (t.isNullOrBlank()) listen() else ask(t)
            }
            override fun onError(e: Int) {
                if (!active) return
                if (e == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) { error = "মাইক পারমিশন দিন"; stop() }
                else ui.postDelayed({ listen() }, 700)
            }
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(b: Bundle?) {}
            override fun onEvent(t: Int, b: Bundle?) {}
        })
        r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
        })
    }

    private fun ask(text: String) {
        state = S.THINKING
        log.add("user" to text)
        val hist = log.takeLast(20).toList()
        Thread {
            val r = runCatching { gemini(hist) }
            ui.post {
                if (!active) return@post
                r.onSuccess { log.add("model" to it); speak(it) }
                r.onFailure { error = it.message ?: "Error"; stop() }
            }
        }.start()
    }

    private fun post(m: String, body: JSONObject): String {
        val c = URL("https://generativelanguage.googleapis.com/v1beta/models/${m.trim()}:generateContent")
            .openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.connectTimeout = 20000; c.readTimeout = 60000; c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        c.setRequestProperty("x-goog-api-key", apiKey.trim())
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = c.responseCode
        val txt = (if (code in 200..299) c.inputStream else c.errorStream).bufferedReader().use { it.readText() }
        if (code !in 200..299) throw Exception("API $code: " + txt.take(160))
        return txt
    }

    private fun gemini(h: List<Pair<String, String>>): String {
        val contents = JSONArray()
        h.forEach { (role, t) ->
            contents.put(JSONObject().put("role", role).put("parts", JSONArray().put(JSONObject().put("text", t))))
        }
        val sys = SYS + (PERSONAS[persona] ?: "")
        val body = JSONObject().put("contents", contents)
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", sys))))
        return JSONObject(post(model, body)).getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text").trim()
    }

    private fun geminiTts(t: String): ByteArray {
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(
                JSONObject().put("text", "Read aloud in a warm, natural, expressive female voice: $t")))))
            .put("generationConfig", JSONObject().put("responseModalities", JSONArray().put("AUDIO"))
                .put("speechConfig", JSONObject().put("voiceConfig", JSONObject().put("prebuiltVoiceConfig",
                    JSONObject().put("voiceName", voice)))))
        val d = JSONObject(post(ttsModel, body)).getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts").getJSONObject(0)
            .getJSONObject("inlineData").getString("data")
        return Base64.decode(d, Base64.DEFAULT)
    }

    private fun speak(t: String) {
        state = S.SPEAKING
        val my = ++gen
        val clean = t.replace(Regex("[*#_`]"), "")
        if (!natural) { sysSpeak(clean, my); return }
        Thread {
            val pcm = runCatching { geminiTts(clean) }
            ui.post {
                if (my != gen || !active) return@post
                pcm.onSuccess { note = ""; Thread { playPcm(it, my) }.start() }
                pcm.onFailure { note = "Natural voice পাওয়া যায়নি, সিস্টেম ভয়েস চলছে"; sysSpeak(clean, my) }
            }
        }.start()
    }

    private fun sysSpeak(t: String, my: Int) {
        if (!ttsReady) { afterSpeak(); return }
        val loc = Locale.forLanguageTag(lang)
        val ok = (tts?.isLanguageAvailable(loc) ?: TextToSpeech.LANG_NOT_SUPPORTED) >= TextToSpeech.LANG_AVAILABLE
        if (ok) tts?.language = loc
        tts?.speak(t, TextToSpeech.QUEUE_FLUSH, null, my.toString())
    }

    private fun playPcm(pcm: ByteArray, my: Int) {
        try {
            val min = AudioTrack.getMinBufferSize(24000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val at = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(24000)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(maxOf(min, 4096)).setTransferMode(AudioTrack.MODE_STREAM).build()
            track = at; playing = true; at.play()
            var off = 0
            while (off < pcm.size && playing) {
                val n = at.write(pcm, off, minOf(4096, pcm.size - off))
                if (n <= 0) break
                off += n
            }
            if (playing) {
                at.stop()
                var w = 0
                while (playing && at.playbackHeadPosition < pcm.size / 2 && w < 600) { Thread.sleep(50); w++ }
            }
            at.release()
        } catch (e: Exception) { }
        playing = false
        ui.post { if (my == gen) afterSpeak() }
    }

    private fun afterSpeak() { if (active && loop) listen() else { active = false; state = S.IDLE } }
}

class MainActivity : ComponentActivity() {
    private lateinit var e: Engine
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        e = Engine(this)
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { Screen(e) } }
    }
    override fun onDestroy() { e.release(); super.onDestroy() }
}

@Composable
fun Chips(opts: List<String>, sel: String, on: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState())) {
        opts.forEach { o ->
            FilterChip(selected = o == sel, onClick = { on(o) }, label = { Text(o) }, modifier = Modifier.padding(end = 6.dp))
        }
    }
}

@Composable
fun Screen(e: Engine) {
    val ctx = LocalContext.current
    var settings by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var now by remember { mutableStateOf("") }
    val t = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val f = SimpleDateFormat("hh:mm:ss a", Locale.US)
        while (true) {
            withFrameNanos { ns -> t.floatValue = (ns / 1e9 % 3600.0).toFloat() }
            val s = f.format(Date())
            if (s != now) now = s
        }
    }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) e.start() else e.error = "মাইক পারমিশন দরকার"
    }
    val ls = rememberLazyListState()
    LaunchedEffect(e.log.size) { if (e.log.isNotEmpty()) ls.animateScrollToItem(e.log.lastIndex) }

    Surface(Modifier.fillMaxSize(), color = Color(0xFF050B14)) {
        Box(Modifier.fillMaxSize()) {
            if (e.rain) Rain(t, Modifier.fillMaxSize())
            Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(now, color = CYAN, style = MaterialTheme.typography.titleMedium)
                    Text("  ${e.persona} • ${e.voice}", color = CYAN.copy(0.6f), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { settings = true }) { Text("⚙", color = CYAN) }
                }
                Angel(t, e.state, Modifier.weight(1f).fillMaxWidth().clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null) { e.interrupt() })
                Text("● ${e.state.label}", color = CYAN)
                if (e.note.isNotEmpty()) Text(e.note, color = CYAN.copy(0.6f), style = MaterialTheme.typography.bodySmall)
                if (e.error.isNotEmpty()) Text(e.error, color = Color(0xFFFF6B6B), style = MaterialTheme.typography.bodySmall)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 110.dp).background(Color(0x99050B14)), state = ls) {
                    items(e.log) { m ->
                        Text(m.second, color = if (m.first == "user") Color.White else CYAN, modifier = Modifier.padding(3.dp))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(input, { input = it }, Modifier.weight(1f), singleLine = true,
                        placeholder = { Text("টাইপ করে বলুন…") })
                    TextButton(onClick = { e.sendText(input); input = "" }) { Text("➤", color = CYAN) }
                }
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { e.stop() }, border = BorderStroke(1.dp, Color(0xFFFF4D6D))) {
                        Text("TERMINATE", color = Color(0xFFFF4D6D))
                    }
                    Spacer(Modifier.width(16.dp))
                    Button(onClick = {
                        if (ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) e.start()
                        else perm.launch(Manifest.permission.RECORD_AUDIO)
                    }) { Text("🎤") }
                }
            }
        }
    }

    if (settings) AlertDialog(
        onDismissRequest = { e.save(); settings = false },
        confirmButton = { TextButton(onClick = { e.save(); settings = false }) { Text("Save") } },
        dismissButton = { TextButton(onClick = { e.log.clear() }) { Text("Chat মুছুন") } },
        title = { Text("Settings") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("aistudio.google.com থেকে ফ্রি Gemini API key", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(e.apiKey, { e.apiKey = it }, label = { Text("API key") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation())
                OutlinedTextField(e.model, { e.model = it }, label = { Text("Chat model") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Natural মেয়ে কণ্ঠ (Gemini TTS)", Modifier.weight(1f))
                    Switch(e.natural, { e.natural = it })
                }
                OutlinedTextField(e.ttsModel, { e.ttsModel = it }, label = { Text("TTS model") }, singleLine = true)
                Text("কণ্ঠ", Modifier.padding(top = 8.dp))
                Chips(VOICES, e.voice) { e.voice = it }
                Text("ব্যক্তিত্ব", Modifier.padding(top = 8.dp))
                Chips(PERSONAS.keys.toList(), e.persona) { e.persona = it }
                Text("ভাষা", Modifier.padding(top = 8.dp))
                Chips(listOf("bn-BD", "en-US"), e.lang) { e.lang = it }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Hacker rain ব্যাকগ্রাউন্ড", Modifier.weight(1f))
                    Switch(e.rain, { e.rain = it })
                }
            }
        }
    )
}

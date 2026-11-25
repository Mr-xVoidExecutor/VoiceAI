package com.voiceai.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

private const val SYS = "তুমি একজন বন্ধুসুলভ ভয়েস অ্যাসিস্ট্যান্ট। ব্যবহারকারী যে ভাষায় কথা বলে সেই ভাষায় (বাংলা হলে বাংলায়) ১-৩ ছোট বাক্যে উত্তর দাও। মার্কডাউন, তালিকা বা ইমোজি ব্যবহার করবে না।"

enum class S(val label: String) {
    IDLE("মাইক চাপুন"), LISTENING("শুনছি…"), THINKING("ভাবছি…"), SPEAKING("বলছি…")
}

class Engine(private val a: ComponentActivity) {
    private val prefs = a.getSharedPreferences("p", 0)
    private val ui = Handler(Looper.getMainLooper())
    var state by mutableStateOf(S.IDLE)
    var error by mutableStateOf("")
    val log = mutableStateListOf<Pair<String, String>>()
    var apiKey by mutableStateOf(prefs.getString("key", "") ?: "")
    var model by mutableStateOf(prefs.getString("model", "gemini-2.5-flash") ?: "gemini-2.5-flash")
    var lang by mutableStateOf(prefs.getString("lang", "bn-BD") ?: "bn-BD")
    private var active = false
    private var sr: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    init {
        tts = TextToSpeech(a) { st ->
            ttsReady = st == TextToSpeech.SUCCESS
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) { ui.post { afterSpeak() } }
                override fun onError(id: String?) { ui.post { afterSpeak() } }
            })
        }
    }

    fun save() {
        prefs.edit().putString("key", apiKey.trim()).putString("model", model.trim()).putString("lang", lang).apply()
    }

    fun start() {
        if (apiKey.isBlank()) { error = "⚙ Settings থেকে API key দিন"; return }
        if (!SpeechRecognizer.isRecognitionAvailable(a)) { error = "এই ফোনে Speech recognition নেই"; return }
        active = true; error = ""; listen()
    }

    fun stop() {
        active = false
        sr?.cancel(); tts?.stop(); state = S.IDLE
    }

    fun release() { active = false; sr?.destroy(); tts?.shutdown() }

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

    private fun gemini(h: List<Pair<String, String>>): String {
        val contents = JSONArray()
        h.forEach { (role, t) ->
            contents.put(JSONObject().put("role", role).put("parts", JSONArray().put(JSONObject().put("text", t))))
        }
        val body = JSONObject().put("contents", contents)
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SYS))))
        val c = URL("https://generativelanguage.googleapis.com/v1beta/models/${model.trim()}:generateContent")
            .openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.connectTimeout = 20000; c.readTimeout = 40000; c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        c.setRequestProperty("x-goog-api-key", apiKey.trim())
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = c.responseCode
        val txt = (if (code in 200..299) c.inputStream else c.errorStream).bufferedReader().use { it.readText() }
        if (code !in 200..299) throw Exception("API $code: " + txt.take(200))
        return JSONObject(txt).getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text").trim()
    }

    private fun speak(t: String) {
        state = S.SPEAKING
        if (!ttsReady) { afterSpeak(); return }
        val loc = Locale.forLanguageTag(lang)
        val ok = (tts?.isLanguageAvailable(loc) ?: TextToSpeech.LANG_NOT_SUPPORTED) >= TextToSpeech.LANG_AVAILABLE
        if (ok) tts?.language = loc
        tts?.speak(t.replace(Regex("[*#_`]"), ""), TextToSpeech.QUEUE_FLUSH, null, "u")
    }

    private fun afterSpeak() { if (active) listen() else state = S.IDLE }
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
fun Screen(e: Engine) {
    val ctx = LocalContext.current
    val cyan = Color(0xFF22E6D6)
    var settings by remember { mutableStateOf(false) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) e.start() else e.error = "মাইক পারমিশন দরকার"
    }
    val t = rememberInfiniteTransition(label = "p")
    val pulse by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(if (e.state == S.SPEAKING) 600 else 2200, easing = LinearEasing), RepeatMode.Reverse),
        label = "a"
    )
    val ls = rememberLazyListState()
    LaunchedEffect(e.log.size) { if (e.log.isNotEmpty()) ls.animateScrollToItem(e.log.lastIndex) }

    Surface(Modifier.fillMaxSize(), color = Color(0xFF050B14)) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("VOICE AI", color = cyan, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { settings = true }) { Text("⚙", color = cyan) }
            }
            Canvas(Modifier.size(260.dp)) {
                val amp = when (e.state) { S.SPEAKING -> 0.35f; S.LISTENING -> 0.2f; S.THINKING -> 0.1f; else -> 0.04f }
                val base = size.minDimension / 4
                val core = base * 1.6f * (1 + amp * pulse)
                drawCircle(Brush.radialGradient(listOf(cyan.copy(0.9f), cyan.copy(0.2f), Color.Transparent), center, core), core, center)
                for (i in 1..4) drawCircle(cyan.copy(alpha = 0.5f / i), base * (1 + i * 0.25f) * (1 + amp * pulse * i / 2), center, style = Stroke(2f))
            }
            Text("● ${e.state.label}", color = cyan)
            if (e.error.isNotEmpty()) Text(e.error, color = Color(0xFFFF6B6B), modifier = Modifier.padding(top = 4.dp))
            LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp), state = ls) {
                items(e.log) { m ->
                    Text(m.second, color = if (m.first == "user") Color.White else cyan, modifier = Modifier.padding(4.dp))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
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

    if (settings) AlertDialog(
        onDismissRequest = { e.save(); settings = false },
        confirmButton = { TextButton(onClick = { e.save(); settings = false }) { Text("Save") } },
        title = { Text("Settings") },
        text = {
            Column {
                Text("aistudio.google.com থেকে ফ্রি Gemini API key নিন", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(e.apiKey, { e.apiKey = it }, label = { Text("API key") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation())
                OutlinedTextField(e.model, { e.model = it }, label = { Text("Model") }, singleLine = true)
                Row(Modifier.padding(top = 8.dp)) {
                    listOf("bn-BD" to "বাংলা", "en-US" to "English").forEach { (c, n) ->
                        FilterChip(selected = e.lang == c, onClick = { e.lang = c }, label = { Text(n) },
                            modifier = Modifier.padding(end = 8.dp))
                    }
                }
            }
        }
    )
}

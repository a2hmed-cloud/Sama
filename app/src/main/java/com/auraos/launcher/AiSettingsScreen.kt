package com.auraos.launcher

import android.content.Context
import android.media.MediaPlayer
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SettingsVoice
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private object AuraSecureConfig {
    private const val PREFS = "aura_secure_config"
    private const val ALIAS = "aura_config_aes_v1"

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getKey(ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(
            ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build())
        return generator.generateKey()
    }

    fun put(context: Context, name: String, value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val encoded = Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(name, encoded).apply()
    }

    fun get(context: Context, name: String, fallback: String = ""): String {
        return try {
            val encoded = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(name, null)
                ?: return fallback
            val packed = Base64.decode(encoded, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, packed.copyOfRange(0, 12)))
            String(cipher.doFinal(packed.copyOfRange(12, packed.size)), Charsets.UTF_8)
        } catch (_: Exception) { fallback }
    }
}

private object AuraAiApi {
    private fun request(url: String, method: String, key: String, body: JSONObject? = null, headers: Map<String, String> = emptyMap()): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 20000
            connection.readTimeout = 60000
            connection.setRequestProperty("Authorization", "Bearer $key")
            connection.setRequestProperty("Accept", "*/*")
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val bytes = (if (code in 200..299) connection.inputStream else connection.errorStream)?.use { it.readBytes() } ?: ByteArray(0)
            if (code !in 200..299) {
                val raw = String(bytes, Charsets.UTF_8)
                val message = runCatching { JSONObject(raw).optJSONObject("error")?.optString("message") }.getOrNull()
                throw IllegalStateException("HTTP $code: ${message ?: raw.take(200)}")
            }
            return bytes
        } finally { connection.disconnect() }
    }

    fun models(base: String, key: String): List<String> {
        val root = JSONObject(String(request("${base.trim().trimEnd('/')}/models", "GET", key), Charsets.UTF_8))
        val data = root.optJSONArray("data") ?: JSONArray()
        return buildList {
            for (i in 0 until data.length()) data.optJSONObject(i)?.optString("id")?.takeIf { it.isNotBlank() }?.let(::add)
        }.distinct().sorted()
    }

    fun chat(base: String, key: String, model: String): String {
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", "أنت مساعد AURA. تحدث بالمصرية الطبيعية، بإجابة قصيرة وواضحة."))
            .put(JSONObject().put("role", "user").put("content", "عرّف بنفسك في جملة ترحيب مصرية قصيرة."))
        val body = JSONObject().put("model", model).put("messages", messages).put("max_tokens", 120).put("temperature", 0.7)
        val root = JSONObject(String(request("${base.trim().trimEnd('/')}/chat/completions", "POST", key, body), Charsets.UTF_8))
        return root.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
            ?.takeIf { it.isNotBlank() } ?: error("الـ AI رجّع رد فاضي")
    }

    fun speech(key: String, text: String, voiceId: String): ByteArray {
        val body = JSONObject().put("text", text).put("reference_id", voiceId).put("format", "mp3")
        return request("https://api.fish.audio/v1/tts", "POST", key, body,
            mapOf("model" to "s2.1-pro-free", "Accept" to "audio/mpeg"))
    }
}

@Composable
internal fun AiSettingsScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var baseUrl by remember { mutableStateOf(AuraSecureConfig.get(context, "base_url", "https://api.bluesminds.com/v1")) }
    var aiKey by remember { mutableStateOf(AuraSecureConfig.get(context, "ai_key")) }
    var fishKey by remember { mutableStateOf(AuraSecureConfig.get(context, "fish_key")) }
    var voiceId by remember { mutableStateOf(AuraSecureConfig.get(context, "voice_id", "14f1000b77d547eeb5f03b474dd29e0f")) }
    var selectedModel by remember { mutableStateOf(AuraSecureConfig.get(context, "model")) }
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var menuOpen by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("أدخل مفاتيح الخدمة واحفظ الإعدادات لبدء الاختبار.") }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }

    fun saveConfig() {
        AuraSecureConfig.put(context, "base_url", baseUrl.trim().ifBlank { "https://api.bluesminds.com/v1" })
        AuraSecureConfig.put(context, "ai_key", aiKey.trim())
        AuraSecureConfig.put(context, "fish_key", fishKey.trim())
        AuraSecureConfig.put(context, "voice_id", voiceId.trim())
        AuraSecureConfig.put(context, "model", selectedModel)
    }

    DisposableEffect(Unit) { onDispose { player?.release(); player = null } }

    Column(Modifier.fillMaxSize().background(Color(0xFF071426)).statusBarsPadding().navigationBarsPadding()
        .verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, null, tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text("AI & VOICE", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Text("BluesMinds + Fish Audio", color = Color(0xFFA9C5DB), fontSize = 11.sp)
            }
        }
        SettingsGroup("BLUESMINDS • AI") {
            SettingsInput("API Base URL", baseUrl, { baseUrl = it }, "https://api.bluesminds.com/v1")
            SettingsInput("BluesMinds API Key", aiKey, { aiKey = it }, "الصق المفتاح هنا", secret = true)
            Button(onClick = {
                if (aiKey.isBlank()) { status = "أدخل مفتاح BluesMinds الأول."; return@Button }
                busy = true; status = "بنجلب الموديلات..."; saveConfig()
                scope.launch {
                    try {
                        val found = withContext(Dispatchers.IO) { AuraAiApi.models(baseUrl, aiKey) }
                        models = found
                        if (selectedModel !in found) selectedModel = found.firstOrNull().orEmpty()
                        saveConfig()
                        status = "تم جلب ${found.size} موديل."
                    } catch (e: Exception) { status = "تعذر جلب الموديلات: ${e.message}" }
                    finally { busy = false }
                }
            }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Refresh, null); Spacer(Modifier.width(7.dp)); Text("جلب الموديلات")
            }
            Box {
                OutlinedButton(onClick = { menuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (selectedModel.isBlank()) "اختار موديل بعد الجلب" else selectedModel, maxLines = 1)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    models.forEach { model -> DropdownMenuItem(text = { Text(model) }, onClick = {
                        selectedModel = model; menuOpen = false; saveConfig()
                    }) }
                }
            }
        }
        SettingsGroup("FISH AUDIO • VOICE") {
            SettingsInput("Fish Audio API Key", fishKey, { fishKey = it }, "الصق مفتاح Fish Audio", secret = true)
            SettingsInput("Voice Reference ID", voiceId, { voiceId = it }, "Fish Audio voice ID")
            Text("الصوت الافتراضي المحفوظ: مصري، ودود وطبيعي. تقدر تغيّر الـ Reference ID من هنا.", color = Color(0xFFA9C5DB), fontSize = 11.sp)
        }
        Button(onClick = {
            saveConfig()
            if (selectedModel.isBlank() || aiKey.isBlank() || fishKey.isBlank()) {
                status = "لازم مفتاح BluesMinds ومفتاح Fish Audio وموديل مختار."; return@Button
            }
            busy = true; status = "بنختبر AI وبنحوّل الرد لصوت..."
            scope.launch {
                try {
                    val reply = withContext(Dispatchers.IO) { AuraAiApi.chat(baseUrl, aiKey, selectedModel) }
                    val audio = withContext(Dispatchers.IO) { AuraAiApi.speech(fishKey, reply, voiceId) }
                    val file = java.io.File(context.cacheDir, "aura_voice_test.mp3")
                    file.writeBytes(audio)
                    player?.release()
                    player = MediaPlayer().apply {
                        setDataSource(file.absolutePath)
                        setOnPreparedListener { it.start() }
                        setOnCompletionListener { it.release(); if (player === it) player = null }
                        prepareAsync()
                    }
                    status = "نجح اختبار AI وتحويل الصوت. الرد: $reply"
                } catch (e: Exception) { status = "فشل الاختبار: ${e.message}" }
                finally { busy = false }
            }
        }, enabled = !busy, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A83B8))) {
            Icon(Icons.Outlined.SettingsVoice, null); Spacer(Modifier.width(8.dp))
            Text(if (busy) "جاري الاختبار..." else "اختبار AI والصوت")
        }
        OutlinedButton(onClick = { saveConfig(); status = "تم حفظ الإعدادات المشفرة على الجهاز." }, modifier = Modifier.fillMaxWidth()) {
            Text("حفظ الإعدادات")
        }
        Surface(color = Color(0xFF12314B), shape = RoundedCornerShape(16.dp)) {
            Text(status, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(14.dp))
        }
        Text("تنبيه أمان: المفاتيح مش موجودة في ملفات GitHub، وبتتخزن مشفّرة بمفتاح Android Keystore على الجهاز. للحماية الأقوى في نسخة الإنتاج نحتاج Backend وسيط بدل اتصال التطبيق المباشر.", color = Color(0xFF8FAFC7), fontSize = 10.sp)
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Color(0x99183A59), shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(.12f))) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = Color(0xFF72D5FF), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            content()
        }
    }
}

@Composable
private fun SettingsInput(label: String, value: String, onValueChange: (String) -> Unit,
    placeholder: String, secret: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, color = Color(0xFFA9C5DB), fontSize = 11.sp)
        OutlinedTextField(value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, fontSize = 12.sp) }, singleLine = true,
            visualTransformation = if (secret) androidx.compose.ui.text.input.PasswordVisualTransformation()
                else androidx.compose.ui.text.input.VisualTransformation.None,
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF72D5FF), unfocusedBorderColor = Color.White.copy(.18f),
                cursorColor = Color(0xFF72D5FF)))
    }
}

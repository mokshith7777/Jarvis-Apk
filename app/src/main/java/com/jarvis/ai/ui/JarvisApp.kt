package com.jarvis.ai.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private val cyan = Color(0xFF00D9FF)
private val bg = Color(0xFF05070A)
private val panel = Color(0xFF101820)
private val client = OkHttpClient.Builder().connectTimeout(25, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()

private data class ChatMessage(val role: String, val text: String)

@Composable
fun JarvisApp() {
    val context = LocalContext.current
    val prefs = remember { securePrefs(context) }
    var page by remember { mutableStateOf("Home") }
    var providerUrl by remember { mutableStateOf(prefs.getString("url", "https://api.groq.com/openai/v1") ?: "") }
    var model by remember { mutableStateOf(prefs.getString("model", "llama-3.3-70b-versatile") ?: "") }
    var apiKey by remember { mutableStateOf(prefs.getString("key", "") ?: "") }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Ready • Configure provider to begin") }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    val scope = rememberCoroutineScope()

    Scaffold(containerColor = bg, bottomBar = {
        NavigationBar(containerColor = Color(0xFF0B1015)) {
            listOf("Home", "Chat", "Settings").forEach { item ->
                NavigationBarItem(selected = page == item, onClick = { page = item },
                    icon = { Text(item.take(1), color = if (page == item) cyan else Color.Gray) },
                    label = { Text(item) })
            }
        }
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("JARVIS", color = cyan, fontSize = 27.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                    Text("PERSONAL AI • ANDROID", color = Color(0xFF72818B), fontSize = 10.sp, letterSpacing = 2.sp)
                }
                Text(if (busy) "● WORKING" else "● ONLINE", color = if (busy) Color(0xFFFF5268) else cyan, fontSize = 11.sp)
            }
            when (page) {
                "Home" -> {
                    Card(colors = CardDefaults.cardColors(containerColor = panel), shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("SYSTEM STATUS", color = Color.Gray, fontSize = 11.sp, letterSpacing = 2.sp)
                            Text("JARVIS is ready", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                            Text(status, color = Color(0xFFB3C2CA), fontSize = 14.sp)
                            Text("Inference provider: ${if (apiKey.isBlank()) "Not configured" else "Credential saved"}", color = cyan, fontSize = 13.sp)
                        }
                    }
                    Text("QUICK ACTIONS", color = Color.Gray, fontSize = 11.sp, letterSpacing = 2.sp)
                    listOf("Open Chat", "Configure AI Provider").forEach { action ->
                        Card(onClick = { page = if (action == "Open Chat") "Chat" else "Settings" }, colors = CardDefaults.cardColors(containerColor = panel)) {
                            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(action, color = Color.White, modifier = Modifier.weight(1f))
                                Text("›", color = cyan, fontSize = 24.sp)
                            }
                        }
                    }
                    Text("This build provides live AI chat through an OpenAI-compatible API. Agent tools and persistent memory are being added in subsequent builds.", color = Color(0xFF8999A4), fontSize = 12.sp)
                }
                "Settings" -> {
                    Text("AI CONNECTION", color = Color.Gray, fontSize = 11.sp, letterSpacing = 2.sp)
                    OutlinedTextField(providerUrl, { providerUrl = it }, label = { Text("API Base URL") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(model, { model = it }, label = { Text("Model ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(apiKey, { apiKey = it }, label = { Text("API Key") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation())
                    Button(onClick = {
                        prefs.edit().putString("url", providerUrl.trim()).putString("model", model.trim()).putString("key", apiKey.trim()).apply()
                        status = "Provider settings saved securely on this device"
                    }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = cyan)) { Text("SAVE CONFIGURATION", color = Color.Black) }
                    Text("Compatible with Groq, OpenRouter, OpenAI-compatible endpoints, and other providers. Never share your API key.", color = Color(0xFF8C9AA4), fontSize = 12.sp)
                    Text(status, color = cyan, fontSize = 12.sp)
                }
                else -> {
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(messages) { msg ->
                            Card(colors = CardDefaults.cardColors(containerColor = if (msg.role == "user") Color(0xFF12303A) else panel), shape = RoundedCornerShape(14.dp)) {
                                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                                    Text(if (msg.role == "user") "YOU" else "JARVIS", color = cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(5.dp))
                                    Text(msg.text, color = Color.White)
                                }
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(input, { input = it }, modifier = Modifier.weight(1f), placeholder = { Text("Message JARVIS...") }, enabled = !busy)
                        Spacer(Modifier.width(8.dp))
                        Button(enabled = input.isNotBlank() && !busy, onClick = {
                            val prompt = input.trim(); input = ""; messages.add(ChatMessage("user", prompt)); busy = true
                            scope.launch {
                                val answer = withContext(Dispatchers.IO) { requestAI(providerUrl, model, apiKey, messages.toList()) }
                                messages.add(ChatMessage("assistant", answer))
                                status = if (answer.startsWith("Connection error:") || answer.startsWith("API error:")) answer else "Response received"
                                busy = false
                            }
                        }, colors = ButtonDefaults.buttonColors(containerColor = cyan)) { Text("SEND", color = Color.Black) }
                    }
                    if (busy) Text("JARVIS is processing...", color = cyan, fontSize = 12.sp)
                }
            }
        }
    }
}

private fun securePrefs(context: Context) = EncryptedSharedPreferences.create(
    context,
    "jarvis_secure_settings",
    MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
)

private fun requestAI(base: String, model: String, key: String, history: List<ChatMessage>): String {
    if (key.isBlank()) return "API key missing. Open Settings and configure your provider."
    return try {
        val msgs = JSONArray()
        msgs.put(JSONObject().put("role", "system").put("content", "You are JARVIS, a helpful, concise AI assistant."))
        history.forEach { msgs.put(JSONObject().put("role", it.role).put("content", it.text)) }
        val payload = JSONObject().put("model", model).put("messages", msgs).put("stream", false)
        val endpoint = base.trimEnd('/') + "/chat/completions"
        val req = Request.Builder().url(endpoint).header("Authorization", "Bearer ${key.trim()}").header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType())).build()
        client.newCall(req).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) "API error: HTTP ${response.code} • ${body.take(500)}"
            else JSONObject(body).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
                ?: "Unexpected API response."
        }
    } catch (e: Exception) { "Connection error: ${e.message ?: e.javaClass.simpleName}" }
}

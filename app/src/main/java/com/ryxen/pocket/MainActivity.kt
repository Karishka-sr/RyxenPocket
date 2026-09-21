package com.ryxen.pocket

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.createSupabaseClient
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private const val SUPABASE_URL = "https://ebsmxiywavrgzydzmgxi.supabase.co"
private const val SUPABASE_KEY = "sb_publishable_sfyl1NWIgWGLFp6D50PtVg_4wX7m_KX"
private val supabase = createSupabaseClient(SUPABASE_URL, SUPABASE_KEY) { install(Auth) }

private data class Note(val id: String, val title: String, val body: String, val favorite: Boolean)

private class NoteStore(context: Context) {
    private val prefs = context.getSharedPreferences("ryxen_pocket", Context.MODE_PRIVATE)
    fun load(): List<Note> = runCatching {
        val a = JSONArray(prefs.getString("notes", "[]"))
        List(a.length()) { i ->
            val o = a.getJSONObject(i)
            Note(o.getString("id"), o.getString("title"), o.getString("body"), o.optBoolean("favorite"))
        }
    }.getOrDefault(emptyList())
    fun save(notes: List<Note>) {
        val a = JSONArray()
        notes.forEach { n -> a.put(JSONObject().apply { put("id", n.id); put("title", n.title); put("body", n.body); put("favorite", n.favorite) }) }
        prefs.edit().putString("notes", a.toString()).apply()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RyxenPocketApp(applicationContext) }
    }
}

@Composable
fun RyxenPocketApp(context: Context) {
    var mode by remember { mutableStateOf("start") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var repeatPassword by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var loggedIn by remember { mutableStateOf(false) }
    var sessionChecked by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        loggedIn = runCatching { supabase.auth.currentSessionOrNull() != null }.getOrDefault(false)
        sessionChecked = true
    }

    MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFFB85CDE), secondary = Color(0xFF7C63D9))) {
        Surface(Modifier.fillMaxSize(), Color(0xFFFFF4FA)) {
            if (!sessionChecked) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Мяу~ загружаем Ryxen Pocket 🐾") }
            } else if (loggedIn) {
                PocketShell(onLogout = {
                    scope.launch { runCatching { supabase.auth.signOut() }; loggedIn = false; mode = "start"; message = "Ты вышел из аккаунта 🐾" }
                }, context = context)
            } else AuthScreen(mode, email, password, username, repeatPassword, busy, message,
                { mode = it; message = "" }, { email = it }, { password = it }, { username = it }, { repeatPassword = it },
                { scope.launch { busy = true; message = ""; try { supabase.auth.signInWith(Email) { this.email = email.trim(); this.password = password }; loggedIn = true } catch (e: Exception) { message = e.message ?: "Не удалось войти 😿" } finally { busy = false } } },
                { scope.launch { busy = true; message = ""; try {
                    if (email.trim().isEmpty() || password.isEmpty()) message = "Заполни email и пароль ✉️"
                    else if (username.trim().length !in 3..32) message = "Имя пользователя: от 3 до 32 символов."
                    else if (!email.trim().contains("@")) message = "Проверь email ✉️"
                    else if (password.length < 6) message = "Пароль должен содержать минимум 6 символов."
                    else if (password != repeatPassword) message = "Пароли не совпадают 😿"
                    else { supabase.auth.signUpWith(Email) { this.email = email.trim(); this.password = password; data = buildJsonObject { put("username", username.trim()) } }; message = "Аккаунт создан! Проверь email, если требуется подтверждение ✨"; mode = "login" }
                } catch (e: Exception) { message = e.message ?: "Не удалось зарегистрироваться 😿" } finally { busy = false } } })
        }
    }
}

@Composable
private fun AuthScreen(mode: String, email: String, password: String, username: String, repeatPassword: String, busy: Boolean, message: String,
    onModeChange: (String) -> Unit, onEmailChange: (String) -> Unit, onPasswordChange: (String) -> Unit, onUsernameChange: (String) -> Unit, onRepeatPasswordChange: (String) -> Unit,
    onLogin: () -> Unit, onRegister: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("🐾 Ryxen Pocket", fontSize = 30.sp); Text("мяу~ твой маленький уголок :3", color = Color(0xFF7B6B80)); Spacer(Modifier.height(28.dp))
        when (mode) {
            "start" -> { Button({ onModeChange("login") }, Modifier.fillMaxWidth()) { Text("✨ Войти") }; Spacer(Modifier.height(12.dp)); OutlinedButton({ onModeChange("register") }, Modifier.fillMaxWidth()) { Text("🐱 Регистрация") } }
            "login" -> { AuthTitle("Вход"); Field("Email", email, onEmailChange); Field("Пароль", password, onPasswordChange, true); Button(onClick = onLogin, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Входим…" else "Войти →") }; TextButton(onClick = { onModeChange("register") }, enabled = !busy) { Text("Создать аккаунт") }; TextButton(onClick = { onModeChange("start") }, enabled = !busy) { Text("Назад") } }
            "register" -> { AuthTitle("Регистрация"); Field("Имя пользователя", username, onUsernameChange); Field("Email", email, onEmailChange); Field("Пароль", password, onPasswordChange, true); Field("Повтори пароль", repeatPassword, onRepeatPasswordChange, true); Button(onClick = onRegister, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Создаём…" else "Создать аккаунт →") }; TextButton(onClick = { onModeChange("login") }, enabled = !busy) { Text("У меня уже есть аккаунт") }; TextButton(onClick = { onModeChange("start") }, enabled = !busy) { Text("Назад") } }
        }
        if (message.isNotEmpty()) { Spacer(Modifier.height(14.dp)); Text(message) }
    }
}

@Composable private fun AuthTitle(t: String) { Text(t, style = MaterialTheme.typography.headlineSmall); Spacer(Modifier.height(14.dp)) }
@Composable private fun Field(label: String, value: String, change: (String) -> Unit, password: Boolean = false) { OutlinedTextField(value, change, label = { Text(label) }, singleLine = true, visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(10.dp)) }

@Composable
private fun PocketShell(onLogout: () -> Unit, context: Context) {
    val store = remember { NoteStore(context) }
    var notes by remember { mutableStateOf(store.load()) }
    var tab by remember { mutableStateOf(0) }
    var dialog by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Note?>(null) }
    fun save(list: List<Note>) { notes = list; store.save(list) }

    Scaffold(bottomBar = {
        NavigationBar { listOf("🏠" to "Главная", "🐾" to "Заметки", "⭐" to "Избранное", "⚙️" to "Настройки").forEachIndexed { i, pair ->
            NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Text(pair.first) }, label = { Text(pair.second) })
        } }
    }) { pad ->
        Box(Modifier.fillMaxSize().padding(pad).padding(20.dp)) {
            when (tab) {
                0 -> HomeTab(notes.size, notes.count { it.favorite }, { tab = 1 }, { tab = 2 })
                1 -> NotesTab(notes, { selected = it; dialog = true }, { selected = null; dialog = true }, { n -> save(notes.map { if (it.id == n.id) n else it) } }, { id -> save(notes.filterNot { it.id == id }) })
                2 -> NotesTab(notes.filter { it.favorite }, { selected = it; dialog = true }, { tab = 1; selected = null; dialog = true }, { n -> save(notes.map { if (it.id == n.id) n else it) } }, { id -> save(notes.filterNot { it.id == id }) }, true)
                3 -> SettingsTab(onLogout)
            }
            if (dialog) NoteDialog(selected, { title, body ->
                val n = selected?.copy(title = title, body = body) ?: Note(UUID.randomUUID().toString(), title, body, false)
                save(if (selected == null) listOf(n) + notes else notes.map { if (it.id == n.id) n else it }); dialog = false; selected = null
            }, { dialog = false; selected = null })
        }
    }
}

@Composable private fun Header(title: String, subtitle: String) {
    Text(title, fontSize = 28.sp, style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(4.dp))
    Text(subtitle, color = Color(0xFF7B6B80))
    Spacer(Modifier.height(18.dp))
}
@Composable private fun HomeTab(count: Int, fav: Int, notes: () -> Unit, favorites: () -> Unit) { Column(Modifier.fillMaxSize()) { Header("Ryxen Pocket 🐱", "Твой маленький уголок :3"); Card(RoundedCornerShape(24.dp), Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp)) { Text("Мяу! Всё работает ✨", fontSize = 20.sp); Text("Локальных заметок: $count\nВ избранном: $fav"); Spacer(Modifier.height(14.dp)); Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Button(notes) { Text("🐾 Заметки") }; OutlinedButton(favorites) { Text("⭐ Избранное") } } } } } }

@Composable private fun NotesTab(notes: List<Note>, edit: (Note) -> Unit, add: () -> Unit, update: (Note) -> Unit, remove: (String) -> Unit, favoriteOnly: Boolean = false) {
    Column(Modifier.fillMaxSize()) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(if (favoriteOnly) "Избранное ⭐" else "Заметки 🐾", fontSize = 28.sp); Button(add) { Text("+") } }; Spacer(Modifier.height(12.dp));
        if (notes.isEmpty()) Text(if (favoriteOnly) "Пока нет избранных заметок ✨" else "Пока пусто. Создай первую заметку :3")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) { items(notes, key = { it.id }) { n -> Card(RoundedCornerShape(20.dp), Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(n.title.ifBlank { "Без названия" }, fontSize = 18.sp); Text(if (n.favorite) "⭐" else "☆", Modifier) }; if (n.body.isNotBlank()) { Spacer(Modifier.height(6.dp)); Text(n.body) }; Spacer(Modifier.height(10.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { TextButton({ edit(n) }) { Text("Изменить") }; TextButton({ update(n.copy(favorite = !n.favorite)) }) { Text(if (n.favorite) "Убрать из ⭐" else "В ⭐") }; TextButton({ remove(n.id) }) { Text("Удалить") } } } } } }
    }
}

@Composable private fun NoteDialog(existing: Note?, save: (String, String) -> Unit, cancel: () -> Unit) { var title by remember(existing) { mutableStateOf(existing?.title ?: "") }; var body by remember(existing) { mutableStateOf(existing?.body ?: "") }; AlertDialog(onDismissRequest = cancel, title = { Text(if (existing == null) "Новая заметка 🐾" else "Изменить заметку") }, text = { Column { OutlinedTextField(title, { title = it }, label = { Text("Заголовок") }, singleLine = true); Spacer(Modifier.height(8.dp)); OutlinedTextField(body, { body = it }, label = { Text("Текст") }, minLines = 4) } }, confirmButton = { Button({ save(title.trim(), body.trim()) }) { Text("Сохранить") } }, dismissButton = { TextButton(cancel) { Text("Отмена") } }) }

@Composable private fun SettingsTab(onLogout: () -> Unit) { Column(Modifier.fillMaxSize()) { Header("Настройки ⚙️", "Ryxen Pocket"); Card(RoundedCornerShape(22.dp), Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp)) { Text("🌸 Cute mode ON", fontSize = 18.sp); Text("Данные заметок хранятся локально на устройстве."); Spacer(Modifier.height(14.dp)); Text("Версия 0.4.1") } }; Spacer(Modifier.height(20.dp)); OutlinedButton(onLogout, Modifier.fillMaxWidth()) { Text("Выйти из аккаунта") } } }

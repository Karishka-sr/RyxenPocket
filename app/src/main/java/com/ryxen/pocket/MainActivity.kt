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
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.createSupabaseClient
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private const val SUPABASE_URL =
    "https://ebsmxiywavrgzydzmgxi.supabase.co"

private const val SUPABASE_KEY =
    "sb_publishable_sfyl1NWIgWGLFp6D50PtVg_4wX7m_KX"

private val supabase = createSupabaseClient(
    supabaseUrl = SUPABASE_URL,
    supabaseKey = SUPABASE_KEY
) {
    install(Auth)
}

private data class Note(
    val id: String,
    val title: String,
    val body: String,
    val favorite: Boolean
)

private class NoteStore(context: Context) {

    private val prefs =
        context.getSharedPreferences(
            "ryxen_pocket",
            Context.MODE_PRIVATE
        )

    fun load(): List<Note> = runCatching {
        val array = JSONArray(
            prefs.getString("notes", "[]") ?: "[]"
        )

        List(array.length()) { index ->
            val obj = array.getJSONObject(index)

            Note(
                id = obj.getString("id"),
                title = obj.getString("title"),
                body = obj.getString("body"),
                favorite = obj.optBoolean("favorite", false)
            )
        }
    }.getOrDefault(emptyList())

    fun save(notes: List<Note>) {
        val array = JSONArray()

        notes.forEach { note ->
            array.put(
                JSONObject().apply {
                    put("id", note.id)
                    put("title", note.title)
                    put("body", note.body)
                    put("favorite", note.favorite)
                }
            )
        }

        prefs.edit()
            .putString("notes", array.toString())
            .apply()
    }
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            RyxenPocketApp(applicationContext)
        }
    }
}

@Composable
private fun RyxenPocketApp(context: Context) {

    var mode by remember {
        mutableStateOf("start")
    }

    var email by remember {
        mutableStateOf("")
    }

    var password by remember {
        mutableStateOf("")
    }

    var username by remember {
        mutableStateOf("")
    }

    var repeatPassword by remember {
        mutableStateOf("")
    }

    var busy by remember {
        mutableStateOf(false)
    }

    var message by remember {
        mutableStateOf("")
    }

    var loggedIn by remember {
        mutableStateOf(false)
    }

    var sessionChecked by remember {
        mutableStateOf(false)
    }

    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        loggedIn = runCatching {
            supabase.auth.currentSessionOrNull() != null
        }.getOrDefault(false)

        sessionChecked = true
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFFB85CDE),
            secondary = Color(0xFF7C63D9)
        )
    ) {

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFFFF4FA)
        ) {

            when {
                !sessionChecked -> {

                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Мяу~ загружаем Ryxen Pocket 🐾"
                        )
                    }
                }

                loggedIn -> {

                    PocketShell(
                        context = context,
                        onLogout = {

                            scope.launch {

                                runCatching {
                                    supabase.auth.signOut()
                                }

                                loggedIn = false
                                mode = "start"
                                message = "Ты вышел из аккаунта 🐾"
                            }
                        }
                    )
                }

                else -> {

                    AuthScreen(
                        mode = mode,
                        email = email,
                        password = password,
                        username = username,
                        repeatPassword = repeatPassword,
                        busy = busy,
                        message = message,

                        onModeChange = {
                            mode = it
                            message = ""
                        },

                        onEmailChange = {
                            email = it
                        },

                        onPasswordChange = {
                            password = it
                        },

                        onUsernameChange = {
                            username = it
                        },

                        onRepeatPasswordChange = {
                            repeatPassword = it
                        },

                        onLogin = {

                            scope.launch {

                                busy = true
                                message = ""

                                try {

                                    supabase.auth.signInWith(Email) {
                                        this.email = email.trim()
                                        this.password = password
                                    }

                                    loggedIn = true

                                } catch (e: Exception) {

                                    message =
                                        e.message
                                            ?: "Не удалось войти 😿"

                                } finally {

                                    busy = false
                                }
                            }
                        },

                        onRegister = {

                            scope.launch {

                                busy = true
                                message = ""

                                try {

                                    val cleanEmail = email.trim()
                                    val cleanUsername = username.trim()

                                    when {

                                        cleanEmail.isEmpty() ||
                                            password.isEmpty() -> {

                                            message =
                                                "Заполни email и пароль ✉️"
                                        }

                                        cleanUsername.length !in 3..32 -> {

                                            message =
                                                "Имя пользователя: от 3 до 32 символов."
                                        }

                                        !cleanEmail.contains("@") -> {

                                            message =
                                                "Проверь email ✉️"
                                        }

                                        password.length < 6 -> {

                                            message =
                                                "Пароль должен содержать минимум 6 символов."
                                        }

                                        password != repeatPassword -> {

                                            message =
                                                "Пароли не совпадают 😿"
                                        }

                                        else -> {

                                            supabase.auth.signUpWith(Email) {

                                                this.email = cleanEmail
                                                this.password = password

                                                data = buildJsonObject {
                                                    put(
                                                        "username",
                                                        cleanUsername
                                                    )
                                                }
                                            }

                                            message =
                                                "Аккаунт создан! Проверь email, если требуется подтверждение ✨"

                                            mode = "login"
                                        }
                                    }

                                } catch (e: Exception) {

                                    message =
                                        e.message
                                            ?: "Не удалось зарегистрироваться 😿"

                                } finally {

                                    busy = false
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthScreen(
    mode: String,
    email: String,
    password: String,
    username: String,
    repeatPassword: String,
    busy: Boolean,
    message: String,
    onModeChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onRepeatPasswordChange: (String) -> Unit,
    onLogin: () -> Unit,
    onRegister: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "🐾 Ryxen Pocket",
            fontSize = 30.sp
        )

        Spacer(
            modifier = Modifier.height(6.dp)
        )

        Text(
            text = "мяу~ твой маленький уголок :3",
            color = Color(0xFF7B6B80)
        )

        Spacer(
            modifier = Modifier.height(28.dp)
        )

        when (mode) {

            "start" -> {

                Button(
                    onClick = {
                        onModeChange("login")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("✨ Войти")
                }

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                OutlinedButton(
                    onClick = {
                        onModeChange("register")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🐱 Регистрация")
                }
            }

            "login" -> {

                AuthTitle("Вход")

                Field(
                    label = "Email",
                    value = email,
                    change = onEmailChange
                )

                Field(
                    label = "Пароль",
                    value = password,
                    change = onPasswordChange,
                    password = true
                )

                Button(
                    onClick = onLogin,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (busy)
                            "Входим…"
                        else
                            "Войти →"
                    )
                }

                TextButton(
                    onClick = {
                        onModeChange("register")
                    },
                    enabled = !busy
                ) {
                    Text("Создать аккаунт")
                }

                TextButton(
                    onClick = {
                        onModeChange("start")
                    },
                    enabled = !busy
                ) {
                    Text("Назад")
                }
            }

            "register" -> {

                AuthTitle("Регистрация")

                Field(
                    label = "Имя пользователя",
                    value = username,
                    change = onUsernameChange
                )

                Field(
                    label = "Email",
                    value = email,
                    change = onEmailChange
                )

                Field(
                    label = "Пароль",
                    value = password,
                    change = onPasswordChange,
                    password = true
                )

                Field(
                    label = "Повтори пароль",
                    value = repeatPassword,
                    change = onRepeatPasswordChange,
                    password = true
                )

                Button(
                    onClick = onRegister,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (busy)
                            "Создаём…"
                        else
                            "Создать аккаунт →"
                    )
                }

                TextButton(
                    onClick = {
                        onModeChange("login")
                    },
                    enabled = !busy
                ) {
                    Text("У меня уже есть аккаунт")
                }

                TextButton(
                    onClick = {
                        onModeChange("start")
                    },
                    enabled = !busy
                ) {
                    Text("Назад")
                }
            }
        }

        if (message.isNotEmpty()) {

            Spacer(
                modifier = Modifier.height(14.dp)
            )

            Text(message)
        }
    }
}

@Composable
private fun AuthTitle(title: String) {

    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall
    )

    Spacer(
        modifier = Modifier.height(14.dp)
    )
}

@Composable
private fun Field(
    label: String,
    value: String,
    change: (String) -> Unit,
    password: Boolean = false
) {

    OutlinedTextField(
        value = value,
        onValueChange = change,
        label = {
            Text(label)
        },
        singleLine = true,
        visualTransformation =
            if (password)
                PasswordVisualTransformation()
            else
                VisualTransformation.None,
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(
        modifier = Modifier.height(10.dp)
    )
}

@Composable
private fun PocketShell(
    context: Context,
    onLogout: () -> Unit
) {

    val store = remember {
        NoteStore(context)
    }

    var notes by remember {
        mutableStateOf(store.load())
    }

    var tab by remember {
        mutableStateOf(0)
    }

    var dialog by remember {
        mutableStateOf(false)
    }

    var selected by remember {
        mutableStateOf<Note?>(null)
    }

    fun save(list: List<Note>) {
        notes = list
        store.save(list)
    }

    Scaffold(

        bottomBar = {

            NavigationBar {

                val navigationItems =
                    listOf(
                        "🏠" to "Главная",
                        "🐾" to "Заметки",
                        "⭐" to "Избранное",
                        "⚙️" to "Настройки"
                    )

                navigationItems.forEachIndexed { index, item ->

                    NavigationBarItem(
                        selected = tab == index,

                        onClick = {
                            tab = index
                        },

                        icon = {
                            Text(item.first)
                        },

                        label = {
                            Text(item.second)
                        }
                    )
                }
            }
        }

    ) { padding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
        ) {

            when (tab) {

                0 -> {

                    HomeTab(
                        notesCount = notes.size,

                        favoritesCount =
                            notes.count {
                                it.favorite
                            },

                        onNotes = {
                            tab = 1
                        },

                        onFavorites = {
                            tab = 2
                        }
                    )
                }

                1 -> {

                    NotesTab(
                        notes = notes,

                        onEdit = { note ->
                            selected = note
                            dialog = true
                        },

                        onAdd = {
                            selected = null
                            dialog = true
                        },

                        onUpdate = { note ->

                            save(
                                notes.map { current ->
                                    if (current.id == note.id) {
                                        note
                                    } else {
                                        current
                                    }
                                }
                            )
                        },

                        onDelete = { id ->

                            save(
                                notes.filterNot {
                                    it.id == id
                                }
                            )
                        }
                    )
                }

                2 -> {

                    NotesTab(
                        notes =
                            notes.filter {
                                it.favorite
                            },

                        onEdit = { note ->
                            selected = note
                            dialog = true
                        },

                        onAdd = {
                            tab = 1
                            selected = null
                            dialog = true
                        },

                        onUpdate = { note ->

                            save(
                                notes.map { current ->
                                    if (current.id == note.id) {
                                        note
                                    } else {
                                        current
                                    }
                                }
                            )
                        },

                        onDelete = { id ->

                            save(
                                notes.filterNot {
                                    it.id == id
                                }
                            )
                        }
                    )
                }

                3 -> {

                    SettingsTab(
                        onLogout = onLogout
                    )
                }
            }
        }
    }

    if (dialog) {

        NoteDialog(
            note = selected,

            onDismiss = {
                dialog = false
            },

            onSave = { title, body ->

                if (selected == null) {

                    save(
                        notes + Note(
                            id = UUID.randomUUID().toString(),
                            title = title,
                            body = body,
                            favorite = false
                        )
                    )

                } else {

                    save(
                        notes.map { note ->

                            if (note.id == selected!!.id) {

                                note.copy(
                                    title = title,
                                    body = body
                                )

                            } else {
                                note
                            }
                        }
                    )
                }

                dialog = false
            }
        )
    }
}

@Composable
private fun HomeTab(
    notesCount: Int,
    favoritesCount: Int,
    onNotes: () -> Unit,
    onFavorites: () -> Unit
) {

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement =
            Arrangement.spacedBy(14.dp)
    ) {

        item {

            Text(
                text = "Привет, Риксен! 🐾",
                style =
                    MaterialTheme.typography.headlineMedium
            )

            Text(
                text =
                    "Добро пожаловать в твой маленький уголок :3",
                color = Color(0xFF7B6B80)
            )
        }

        item {

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp)
            ) {

                Column(
                    modifier = Modifier.padding(20.dp)
                ) {

                    Text(
                        text = "🐾 Твои заметки",
                        style =
                            MaterialTheme.typography.titleLarge
                    )

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    Text(
                        text =
                            "Всего заметок: $notesCount"
                    )

                    Spacer(
                        modifier = Modifier.height(14.dp)
                    )

                    Button(
                        onClick = onNotes
                    ) {
                        Text("Открыть заметки →")
                    }
                }
            }
        }

        item {

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp)
            ) {

                Column(
                    modifier = Modifier.padding(20.dp)
                ) {

                    Text(
                        text = "⭐ Избранное",
                        style =
                            MaterialTheme.typography.titleLarge
                    )

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    Text(
                        text =
                            "Избранных заметок: $favoritesCount"
                    )

                    Spacer(
                        modifier = Modifier.height(14.dp)
                    )

                    OutlinedButton(
                        onClick = onFavorites
                    ) {
                        Text("Посмотреть избранное →")
                    }
                }
            }
        }

        item {

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp)
            ) {

                Text(
                    text =
                        "✨ Ryxen Pocket\nмяу~ cute mode ON 🐱",

                    modifier =
                        Modifier.padding(20.dp)
                )
            }
        }
    }
}

@Composable
private fun NotesTab(
    notes: List<Note>,
    onEdit: (Note) -> Unit,
    onAdd: () -> Unit,
    onUpdate: (Note) -> Unit,
    onDelete: (String) -> Unit
) {

    Column(
        modifier = Modifier.fillMaxSize()
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),

            horizontalArrangement =
                Arrangement.SpaceBetween,

            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Text(
                text = "🐾 Заметки",
                style =
                    MaterialTheme.typography.headlineSmall
            )

            Button(
                onClick = onAdd
            ) {
                Text("+")
            }
        }

        Spacer(
            modifier = Modifier.height(14.dp)
        )

        if (notes.isEmpty()) {

            Box(
                modifier = Modifier.fillMaxSize(),

                contentAlignment =
                    Alignment.Center
            ) {

                Text(
                    text =
                        "Пока тут пусто… 🐱\nСоздай первую заметку!"
                )
            }

        } else {

            LazyColumn(
                verticalArrangement =
                    Arrangement.spacedBy(12.dp)
            ) {

                items(
                    items = notes,
                    key = { it.id }
                ) { note ->

                    NoteCard(
                        note = note,

                        onEdit = {
                            onEdit(note)
                        },

                        onFavorite = {

                            onUpdate(
                                note.copy(
                                    favorite =
                                        !note.favorite
                                )
                            )
                        },

                        onDelete = {
                            onDelete(note.id)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun NoteCard(
    note: Note,
    onEdit: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit
) {

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {

        Column(
            modifier = Modifier.padding(16.dp)
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.SpaceBetween,

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Text(
                    text =
                        note.title.ifBlank {
                            "Без названия"
                        },

                    style =
                        MaterialTheme.typography.titleMedium
                )

                TextButton(
                    onClick = onFavorite
                ) {

                    Text(
                        if (note.favorite)
                            "⭐"
                        else
                            "☆"
                    )
                }
            }

            if (note.body.isNotBlank()) {

                Spacer(
                    modifier = Modifier.height(6.dp)
                )

                Text(
                    text = note.body
                )
            }

            Spacer(
                modifier = Modifier.height(10.dp)
            )

            Row {

                TextButton(
                    onClick = onEdit
                ) {
                    Text("Изменить")
                }

                TextButton(
                    onClick = onDelete
                ) {
                    Text("Удалить")
                }
            }
        }
    }
}

@Composable
private fun NoteDialog(
    note: Note?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {

    var title by remember(note?.id) {
        mutableStateOf(
            note?.title ?: ""
        )
    }

    var body by remember(note?.id) {
        mutableStateOf(
            note?.body ?: ""
        )
    }

    AlertDialog(

        onDismissRequest = onDismiss,

        title = {

            Text(
                if (note == null)
                    "Новая заметка 🐾"
                else
                    "Изменить заметку ✨"
            )
        },

        text = {

            Column {

                OutlinedTextField(
                    value = title,

                    onValueChange = {
                        title = it
                    },

                    label = {
                        Text("Название")
                    },

                    singleLine = true,

                    modifier =
                        Modifier.fillMaxWidth()
                )

                Spacer(
                    modifier = Modifier.height(10.dp)
                )

                OutlinedTextField(
                    value = body,

                    onValueChange = {
                        body = it
                    },

                    label = {
                        Text("Текст")
                    },

                    modifier =
                        Modifier.fillMaxWidth(),

                    minLines = 4
                )
            }
        },

        confirmButton = {

            Button(
                onClick = {
                    onSave(
                        title.trim(),
                        body.trim()
                    )
                }
            ) {
                Text("Сохранить")
            }
        },

        dismissButton = {

            TextButton(
                onClick = onDismiss
            ) {
                Text("Отмена")
            }
        }
    )
}
@Composable
private fun SettingsTab(
    onLogout: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "⚙️ Настройки",
                style = MaterialTheme.typography.headlineSmall
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "🐱 Ryxen Pocket",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Твой маленький пушистый уголок."
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    OutlinedButton(
                        onClick = onLogout,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Выйти из аккаунта")
                    }
                }
            }
        }
    }
}
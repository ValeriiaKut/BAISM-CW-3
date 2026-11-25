package com.example.baism3

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.baism3.ui.theme.BAISM3Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class Character(
    val id: Int,
    val name: String,
    val status: String,
    val species: String,
    val gender: String,
    val origin: String,
    val location: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BAISM3Theme {
                var characters by remember { mutableStateOf(emptyList<Character>()) }
                var selected by remember { mutableStateOf<Character?>(null) }
                var idInput by remember { mutableStateOf("") }
                var loading by remember { mutableStateOf(false) }
                var message by remember { mutableStateOf("Wybierz akcję, aby pobrać dane z Rick and Morty API.") }

                fun request(block: suspend () -> Unit) {
                    lifecycleScope.launch {
                        loading = true
                        message = ""
                        try {
                            block()
                        } catch (error: Exception) {
                            message = error.message ?: "Nie udało się pobrać danych."
                        } finally {
                            loading = false
                        }
                    }
                }

                Scaffold { padding ->
                    Surface(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text("Baza postaci RICK & MORTY", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

                            Button(
                                onClick = {
                                    request {
                                        selected = null
                                        characters = emptyList()
                                        val result = withContext(Dispatchers.IO) { fetchAllCharacters() }
                                        characters = result
                                        selected = null
                                        message = "Pobrano ${result.size} postaci."
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !loading
                            ) { Text("Pobierz wszystkie postaci") }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = idInput,
                                    onValueChange = { idInput = it.filter(Char::isDigit) },
                                    label = { Text("ID postaci") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = {
                                        val id = idInput.toIntOrNull()
                                        if (id == null || id < 1) {
                                            message = "Wpisz poprawne ID (np. 1)."
                                        } else {
                                            selected = null
                                            characters = emptyList()
                                            request {
                                                val result = withContext(Dispatchers.IO) { fetchCharacter(id) }
                                                selected = result
                                                message = ""
                                            }
                                        }
                                    },
                                    modifier = Modifier.padding(top = 8.dp),
                                    enabled = !loading
                                ) { Text("Szukaj") }
                            }

                            if (loading) CircularProgressIndicator()
                            if (message.isNotBlank()) Text(message, style = MaterialTheme.typography.bodyMedium)

                            selected?.let { CharacterCard(it) }

                            if (characters.isNotEmpty()) {
                                Text("Postacie (${characters.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    items(characters, key = { it.id }) { CharacterCard(it) }
                                }
                            } else if (selected == null && !loading) {
                                Spacer(Modifier.height(2.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CharacterCard(character: Character) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("#${character.id}  ${character.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("${character.status} • ${character.species} • ${character.gender}", style = MaterialTheme.typography.bodyMedium)
            Text("Pochodzenie: ${character.origin}", style = MaterialTheme.typography.bodySmall)
            Text("Lokalizacja: ${character.location}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun fetchAllCharacters(): List<Character> {
    val all = mutableListOf<Character>()
    var nextUrl: String? = "https://rickandmortyapi.com/api/character"
    while (nextUrl != null) {
        val json = getJson(nextUrl)
        val results = json.getJSONArray("results")
        for (index in 0 until results.length()) all += results.getJSONObject(index).toCharacter()
        val next = json.getJSONObject("info").opt("next")
        nextUrl = when (next) {
            null, JSONObject.NULL -> null
            else -> next.toString().takeIf { it.isNotBlank() && it != "null" }
        }
    }
    return all
}

private fun fetchCharacter(id: Int): Character =
    getJson("https://rickandmortyapi.com/api/character/$id").toCharacter()

private fun getJson(url: String): JSONObject {
    var retryDelayMs = 1_000L
    repeat(4) { attempt ->
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "BAISM3-Android/1.0")
            val status = connection.responseCode
            if (status == 429 && attempt < 3) {
                val serverDelay = connection.getHeaderField("Retry-After")?.toLongOrNull()?.times(1_000)
                Thread.sleep(serverDelay ?: retryDelayMs)
                retryDelayMs *= 2
                return@repeat
            }
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream.bufferedReader().use { it.readText() }
            if (status !in 200..299) {
                val error = runCatching { JSONObject(body).optString("error") }.getOrNull()
                throw IllegalStateException(error?.takeIf { it.isNotBlank() } ?: "Błąd API ($status).")
            }
            return JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }
    throw IllegalStateException("API chwilowo ogranicza liczbę zapytań (429). Spróbuj ponownie za chwilę.")
}

private fun JSONObject.toCharacter() = Character(
    id = getInt("id"),
    name = getString("name"),
    status = getString("status"),
    species = getString("species"),
    gender = getString("gender"),
    origin = getJSONObject("origin").getString("name"),
    location = getJSONObject("location").getString("name")
)

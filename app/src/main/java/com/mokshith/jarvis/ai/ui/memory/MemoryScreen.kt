package com.mokshith.jarvis.ai.ui.memory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mokshith.jarvis.ai.app.AppContainer
import com.mokshith.jarvis.ai.core.TimeUtils
import com.mokshith.jarvis.ai.memory.MemoryEntity
import com.mokshith.jarvis.ai.ui.components.HudPanel
import com.mokshith.jarvis.ai.ui.components.SectionHeader
import com.mokshith.jarvis.ai.ui.components.StatusPill
import com.mokshith.jarvis.ai.ui.theme.JarvisColors
import kotlinx.coroutines.launch

@Composable
fun MemoryScreen(container: AppContainer) {
    val scope = rememberCoroutineScope()
    var activeTab by remember { mutableIntStateOf(0) }
    val allMemories by container.database.memoryDao().observeActive().collectAsState(initial = emptyList())
    val allSkills by container.skillManager.observeSkills().collectAsState(initial = emptyList())

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<MemoryEntity>>(emptyList()) }
    var selectedCategory by remember { mutableStateOf("ALL") }

    var editingId by remember { mutableStateOf<Long?>(null) }
    var memTitle by remember { mutableStateOf("") }
    var memContent by remember { mutableStateOf("") }
    var memCategory by remember { mutableStateOf("USER_FACT") }

    var skillName by remember { mutableStateOf("") }
    var skillDesc by remember { mutableStateOf("") }
    var skillInstructions by remember { mutableStateOf("") }
    var skillKeywords by remember { mutableStateOf("") }

    LaunchedEffect(searchQuery, allMemories) {
        searchResults = if (searchQuery.isBlank()) {
            allMemories
        } else {
            container.memoryPipeline.searchRelevant(searchQuery, maxResults = 30).map { it.memory }
        }
    }

    val displayedMemories = searchResults.filter { selectedCategory == "ALL" || it.category == selectedCategory }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = activeTab, containerColor = JarvisColors.CharcoalSurface) {
            Tab(selected = activeTab == 0, onClick = { activeTab = 0 }, text = { Text("Memories (${allMemories.size})") })
            Tab(selected = activeTab == 1, onClick = { activeTab = 1 }, text = { Text("Skills (${allSkills.size})") })
        }

        if (activeTab == 0) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Full-Text Search (Room FTS4)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                item {
                    val categories = listOf("ALL", "PREFERENCE", "USER_FACT", "PROJECT", "CONVERSATION", "CHECKPOINT")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(categories) { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat },
                                label = { Text(cat) }
                            )
                        }
                    }
                }

                item {
                    HudPanel(accentColor = JarvisColors.CyanDim) {
                        SectionHeader(if (editingId == null) "Add Persistent Memory" else "Edit Memory #$editingId")
                        OutlinedTextField(value = memTitle, onValueChange = { memTitle = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(value = memContent, onValueChange = { memContent = it }, label = { Text("Content") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(listOf("USER_FACT", "PREFERENCE", "PROJECT", "CONVERSATION")) { c ->
                                FilterChip(selected = memCategory == c, onClick = { memCategory = c }, label = { Text(c) })
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    if (memTitle.isNotBlank() && memContent.isNotBlank()) {
                                        scope.launch {
                                            val now = System.currentTimeMillis()
                                            val currentEdit = editingId
                                            if (currentEdit == null) {
                                                container.database.memoryDao().insert(
                                                    MemoryEntity(
                                                        title = memTitle.trim(),
                                                        content = memContent.trim(),
                                                        category = memCategory,
                                                        source = "user_ui",
                                                        createdAt = now,
                                                        updatedAt = now
                                                    )
                                                )
                                            } else {
                                                container.database.memoryDao().getById(currentEdit)?.let { existing ->
                                                    container.database.memoryDao().update(
                                                        existing.copy(
                                                            title = memTitle.trim(),
                                                            content = memContent.trim(),
                                                            category = memCategory,
                                                            updatedAt = now
                                                        )
                                                    )
                                                }
                                            }
                                            editingId = null
                                            memTitle = ""
                                            memContent = ""
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = JarvisColors.CyanGlow, contentColor = JarvisColors.OledBlack)
                            ) { Text(if (editingId == null) "Save Memory" else "Update") }
                            if (editingId != null) {
                                OutlinedButton(onClick = { editingId = null; memTitle = ""; memContent = "" }) { Text("Cancel") }
                            }
                        }
                    }
                }

                items(displayedMemories, key = { it.id }) { mem ->
                    HudPanel {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(mem.title, fontWeight = FontWeight.Bold, color = JarvisColors.TextPrimary, modifier = Modifier.weight(1f))
                            StatusPill(mem.category, JarvisColors.CyanGlow)
                        }
                        Text(mem.content, fontSize = 13.sp, color = JarvisColors.TextPrimary)
                        Text(
                            text = "Source: ${mem.source} • Created: ${TimeUtils.formatTimestamp(mem.createdAt)}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = JarvisColors.TextSecondary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                editingId = mem.id
                                memTitle = mem.title
                                memContent = mem.content
                                memCategory = mem.category
                            }) { Text("Edit") }
                            OutlinedButton(onClick = { scope.launch { container.database.memoryDao().deleteById(mem.id) } }) {
                                Text("Delete", color = JarvisColors.CrimsonAccent)
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    HudPanel(accentColor = JarvisColors.CyanDim) {
                        SectionHeader("Create Local Skill / Routine", "Reusable instructions injected when trigger keywords match")
                        OutlinedTextField(value = skillName, onValueChange = { skillName = it }, label = { Text("Skill Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(value = skillDesc, onValueChange = { skillDesc = it }, label = { Text("Short Description") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(value = skillKeywords, onValueChange = { skillKeywords = it }, label = { Text("Trigger Keywords (comma-separated)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        OutlinedTextField(value = skillInstructions, onValueChange = { skillInstructions = it }, label = { Text("Step-by-Step Instructions") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                        Button(
                            onClick = {
                                if (skillName.isNotBlank() && skillInstructions.isNotBlank()) {
                                    scope.launch {
                                        container.skillManager.saveSkill(skillName, skillDesc, skillInstructions, skillKeywords)
                                        skillName = ""; skillDesc = ""; skillInstructions = ""; skillKeywords = ""
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisColors.CyanGlow, contentColor = JarvisColors.OledBlack)
                        ) { Text("Save Skill") }
                    }
                }
                items(allSkills, key = { it.id }) { skill ->
                    HudPanel {
                        Text(skill.name, fontWeight = FontWeight.Bold, color = JarvisColors.CyanGlow)
                        Text(skill.description, fontSize = 13.sp, color = JarvisColors.TextSecondary)
                        Text(skill.instructions, fontSize = 12.sp, color = JarvisColors.TextPrimary)
                        Text("Triggers: ${skill.triggerKeywords}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = JarvisColors.TextSecondary)
                        OutlinedButton(onClick = { scope.launch { container.skillManager.deleteSkill(skill.id) } }) {
                            Text("Delete Skill", color = JarvisColors.CrimsonAccent)
                        }
                    }
                }
            }
        }
    }
}

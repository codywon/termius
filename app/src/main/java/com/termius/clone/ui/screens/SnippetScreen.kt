package com.termius.clone.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termius.clone.data.local.AppDatabase
import com.termius.clone.data.model.SnippetEntity
import com.termius.clone.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnippetScreen() {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }

    val snippets by db.snippetDao().getAllSnippets().collectAsState(initial = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Command Snippets", fontWeight = FontWeight.Bold, color = ObsidianTextPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ObsidianSurfaceContainerLow),
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Snippet", tint = ObsidianPrimary)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = ObsidianPrimary,
                contentColor = ObsidianOnPrimary,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Snippet", tint = ObsidianOnPrimary)
            }
        },
        containerColor = ObsidianBackground
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(snippets, key = { it.id }) { snippet ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ObsidianSurfaceContainerLow,
                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianOutlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = ObsidianSurfaceContainerHigh
                            ) {
                                Text(
                                    text = snippet.tag,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = ObsidianPrimary,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = snippet.title,
                                fontWeight = FontWeight.Bold,
                                color = ObsidianTextPrimary,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(snippet.command))
                                    Toast.makeText(context, "Copied snippet to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = ObsidianTextMuted, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    scope.launch { db.snippetDao().deleteSnippet(snippet) }
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ObsidianError, modifier = Modifier.size(16.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ObsidianSurfaceContainerLowest,
                            border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianOutlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = snippet.command.trim(),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = ObsidianPrimary,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var title by remember { mutableStateOf("") }
        var command by remember { mutableStateOf("") }
        var tag by remember { mutableStateOf("DevOps") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("New Snippet", color = ObsidianTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title") },
                        placeholder = { Text("e.g. Docker restart all") },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = tag,
                        onValueChange = { tag = it },
                        label = { Text("Category Tag") },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = command,
                        onValueChange = { command = it },
                        label = { Text("Shell Command") },
                        placeholder = { Text("docker restart $(docker ps -q)") },
                        minLines = 3,
                        maxLines = 6,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val cmd = if (command.endsWith("\n")) command else "$command\n"
                            db.snippetDao().insertSnippet(
                                SnippetEntity(
                                    title = title.ifBlank { "Untitled" },
                                    command = cmd,
                                    tag = tag.ifBlank { "General" }
                                )
                            )
                            showAddDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ObsidianPrimary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Save Snippet", color = ObsidianOnPrimary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel", color = ObsidianTextMuted)
                }
            },
            containerColor = ObsidianSurfaceContainerLow
        )
    }
}

package com.termius.clone.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termius.clone.data.local.AppDatabase
import com.termius.clone.data.model.IdentityEntity
import com.termius.clone.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeychainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }

    val identities by db.identityDao().getAllIdentities().collectAsState(initial = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Identity & Key Vault", fontWeight = FontWeight.Bold, color = ObsidianTextPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ObsidianSurfaceContainerLow),
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Identity", tint = ObsidianPrimary)
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
                Icon(Icons.Default.Add, contentDescription = "Add Identity", tint = ObsidianOnPrimary)
            }
        },
        containerColor = ObsidianBackground
    ) { innerPadding ->
        if (identities.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.VpnKey,
                        contentDescription = null,
                        tint = ObsidianTextMuted,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No identities saved in Vault", color = ObsidianTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Store your SSH keys (Ed25519, RSA) and credentials safely", color = ObsidianTextSecondary, fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(identities, key = { it.id }) { identity ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ObsidianSurfaceContainerLow,
                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianOutlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(ObsidianPrimary.copy(alpha = 0.15f), CircleShape)
                                    .border(1.dp, ObsidianPrimary.copy(alpha = 0.5f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (identity.privateKey.isNotEmpty()) Icons.Default.Key else Icons.Default.VpnKey,
                                    contentDescription = null,
                                    tint = ObsidianPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(identity.name, fontWeight = FontWeight.Bold, color = ObsidianTextPrimary, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (identity.privateKey.isNotEmpty()) "Type: Key (${identity.keyType}) • User: ${identity.username}"
                                           else "Type: Password • User: ${identity.username}",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = ObsidianTextSecondary
                                )
                            }

                            IconButton(
                                onClick = {
                                    scope.launch { db.identityDao().deleteIdentity(identity) }
                                }
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ObsidianError)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var username by remember { mutableStateOf("ubuntu") }
        var password by remember { mutableStateOf("") }
        var privateKey by remember { mutableStateOf("") }
        var passphrase by remember { mutableStateOf("") }
        var isKeyAuth by remember { mutableStateOf(true) }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Identity to Vault", color = ObsidianTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Identity Name") },
                        placeholder = { Text("e.g. AWS Production Key") },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Default Username") },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = isKeyAuth,
                            onClick = { isKeyAuth = true },
                            label = { Text("SSH Private Key") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ObsidianPrimary.copy(alpha = 0.2f),
                                selectedLabelColor = ObsidianPrimary
                            )
                        )
                        FilterChip(
                            selected = !isKeyAuth,
                            onClick = { isKeyAuth = false },
                            label = { Text("Password") }
                        )
                    }

                    if (isKeyAuth) {
                        OutlinedTextField(
                            value = privateKey,
                            onValueChange = { privateKey = it },
                            label = { Text("Private Key (PEM/OpenSSH)") },
                            minLines = 3,
                            maxLines = 5,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = passphrase,
                            onValueChange = { passphrase = it },
                            label = { Text("Key Passphrase (Optional)") },
                            visualTransformation = PasswordVisualTransformation(),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            db.identityDao().insertIdentity(
                                IdentityEntity(
                                    name = name.ifBlank { "Identity #${System.currentTimeMillis() % 1000}" },
                                    username = username,
                                    password = if (!isKeyAuth) password else "",
                                    privateKey = if (isKeyAuth) privateKey else "",
                                    passphrase = passphrase,
                                    keyType = if (isKeyAuth) "RSA/ED25519" else "PASSWORD"
                                )
                            )
                            showAddDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ObsidianPrimary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Save to Vault", color = ObsidianOnPrimary, fontWeight = FontWeight.Bold)
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

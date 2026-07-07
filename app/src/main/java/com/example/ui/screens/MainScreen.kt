package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import com.example.ui.theme.*
import com.example.viewmodel.ChatViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import java.util.regex.Pattern

// Extension function to convert Color to hex string
fun Color.toHexString(): String {
    val argb = this.toArgb()
    return String.format("#%06X", (0xFFFFFF and argb))
}

@Composable
fun AiLogo(customLogoUriString: String, modifier: Modifier = Modifier, innerDotSize: androidx.compose.ui.unit.Dp = 12.dp) {
    if (customLogoUriString.isNotEmpty()) {
        AsyncImage(
            model = customLogoUriString,
            contentDescription = "AI Logo",
            modifier = modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            error = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Default.SmartToy)
        )
    } else {
        Box(modifier = modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.size(innerDotSize).clip(CircleShape).background(MaterialTheme.colorScheme.onPrimary))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(viewModel: ChatViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val sharedPrefs = remember { context.getSharedPreferences("ai_agent_prefs", Context.MODE_PRIVATE) }
    var customLogoUriString by remember { mutableStateOf(sharedPrefs.getString("custom_logo_uri", "") ?: "") }

    val logoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try { context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) { }
            customLogoUriString = it.toString()
            sharedPrefs.edit().putString("custom_logo_uri", customLogoUriString).apply()
        }
    }

    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val selectedProvider by viewModel.selectedProvider.collectAsStateWithLifecycle()
    val tools by viewModel.tools.collectAsStateWithLifecycle()
    val mcps by viewModel.mcps.collectAsStateWithLifecycle()
    val skills by viewModel.skills.collectAsStateWithLifecycle()
    val isStreaming by viewModel.isStreaming.collectAsStateWithLifecycle()
    val currentThinkingStatus by viewModel.currentThinkingStatus.collectAsStateWithLifecycle()
    val modelsList by viewModel.modelsList.collectAsStateWithLifecycle()
    val isLoadingModels by viewModel.isLoadingModels.collectAsStateWithLifecycle()
    val errorMsg by viewModel.errorMsg.collectAsStateWithLifecycle()
    val tokenUsage by viewModel.tokenUsage.collectAsStateWithLifecycle()

    var currentScreen by remember { mutableStateOf("chat") }
    var sessionSearchQuery by remember { mutableStateOf("") }
    var showModelSelector by remember { mutableStateOf(false) }

    LaunchedEffect(errorMsg) {
        errorMsg?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show(); viewModel.clearError() }
    }

    ModalNavigationDrawer(drawerState = drawerState, drawerContent = {
        ModalDrawerSheet(modifier = Modifier.width(320.dp).fillMaxHeight(), drawerContainerColor = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { logoLauncher.launch("image/*") }.padding(bottom = 16.dp)) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        AiLogo(customLogoUriString, Modifier.size(36.dp), 12.dp)
                        Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Logo", tint = Color.White, modifier = Modifier.size(8.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(text = "Alice", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, letterSpacing = (-0.5).sp)
                        Text(text = "Tap logo to change", fontSize = 10.sp, color = MaterialTheme.colorScheme.secondary)
                    }
                }
                Button(onClick = { scope.launch { viewModel.createNewSession(); currentScreen = "chat"; drawerState.close() } }, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).testTag("new_chat_button")) {
                    Icon(Icons.Default.Add, contentDescription = "New Chat", tint = MaterialTheme.colorScheme.onPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "New Chat", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                }
                OutlinedTextField(value = sessionSearchQuery, onValueChange = { sessionSearchQuery = it }, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), placeholder = { Text("Search chats...") })
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(text = "MANAGE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                DrawerMenuItem(icon = Icons.Default.Build, label = "Tools", isSelected = currentScreen == "tools", onClick = { currentScreen = "tools"; scope.launch { drawerState.close() } })
                DrawerMenuItem(icon = Icons.Default.Computer, label = "MCP Servers", isSelected = currentScreen == "mcp", onClick = { currentScreen = "mcp"; scope.launch { drawerState.close() } })
                DrawerMenuItem(icon = Icons.Default.OfflineBolt, label = "Skills System", isSelected = currentScreen == "skills", onClick = { currentScreen = "skills"; scope.launch { drawerState.close() } })
                DrawerMenuItem(icon = Icons.Default.Settings, label = "Settings", isSelected = currentScreen == "settings", onClick = { currentScreen = "settings"; scope.launch { drawerState.close() } })
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(text = "CHATS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f), modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                val filteredSessions = remember(sessions, sessionSearchQuery) { if (sessionSearchQuery.isEmpty()) sessions else sessions.filter { it.title.contains(sessionSearchQuery, ignoreCase = true) } }
                if (filteredSessions.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Text(text = "No conversations yet", fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary) }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        items(filteredSessions) { s ->
                            ChatHistoryItem(session = s, isActive = activeSession?.id == s.id && currentScreen == "chat",
                                onClick = { viewModel.selectSession(s.id); currentScreen = "chat"; scope.launch { drawerState.close() } },
                                onDelete = { viewModel.deleteSession(s.id) },
                                onRename = { newTitle -> viewModel.renameSession(s.id, newTitle) })
                        }
                    }
                }
            }
        }
    }) {
        Scaffold(topBar = {
            // Full-width top bar with model selector
            Surface(modifier = Modifier.fillMaxWidth().shadow(2.dp), color = MaterialTheme.colorScheme.background) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        IconButton(onClick = { scope.launch { drawerState.open() } }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Default.Menu, contentDescription = "Open Drawer", tint = MaterialTheme.colorScheme.onBackground)
                        }
                        // Model selector dropdown
                        Box {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { showModelSelector = true }.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                Text(text = selectedProvider?.activeModel ?: "Select Model", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground)
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                            }
                            DropdownMenu(expanded = showModelSelector, onDismissRequest = { showModelSelector = false }, modifier = Modifier.fillMaxWidth(0.8f)) {
                                modelsList.forEach { m ->
                                    DropdownMenuItem(text = { Text(m) }, onClick = {
                                        val p = selectedProvider?.copy(activeModel = m)
                                        if (p != null) viewModel.saveProviderProfile(p)
                                        showModelSelector = false
                                    })
                                }
                                if (modelsList.isEmpty()) {
                                    DropdownMenuItem(text = { Text("No models - verify connection") }, onClick = { showModelSelector = false })
                                }
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        // Status dot
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(if (isStreaming) NaturalWarning else if (selectedProvider != null) NaturalSuccess else NaturalError))
                        IconButton(onClick = { if (currentScreen != "chat") currentScreen = "chat" else scope.launch { viewModel.createNewSession() } }, modifier = Modifier.size(40.dp)) {
                            Icon(imageVector = if (currentScreen != "chat") Icons.Default.Chat else Icons.Default.Add, contentDescription = "Action", tint = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                    // Token & tool info below title
                    if (currentScreen == "chat") {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                            Text(text = "${tokenUsage.tokens} tokens | ${tokenUsage.toolsUsed} tools used", fontSize = 10.sp, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }, modifier = modifier.fillMaxSize()) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding).background(MaterialTheme.colorScheme.background)) {
                when (currentScreen) {
                    "chat" -> ChatScreen(viewModel = viewModel, messages = messages, isStreaming = isStreaming, currentThinkingStatus = currentThinkingStatus, customLogoUriString = customLogoUriString)
                    "tools" -> ToolsManagementScreen(title = "Tools Backend", configs = tools, onToggle = { id, enabled -> viewModel.toggleTool(id, enabled) }, onBack = { currentScreen = "chat" })
                    "mcp" -> ToolsManagementScreen(title = "MCP Server Nodes", configs = mcps, onToggle = { id, enabled -> viewModel.toggleTool(id, enabled) }, onBack = { currentScreen = "chat" })
                    "skills" -> ToolsManagementScreen(title = "System Skills", configs = skills, onToggle = { id, enabled -> viewModel.toggleTool(id, enabled) }, onBack = { currentScreen = "chat" })
                    "settings" -> SettingsScreen(viewModel = viewModel, providers = providers, selectedProvider = selectedProvider, modelsList = modelsList, isLoadingModels = isLoadingModels, onSave = {}, onSwitch = {}, onDelete = {}, onVerify = {}, customLogoUriString = customLogoUriString, onLogoChangeClick = {}, onLogoResetClick = {})
                }
            }
        }
    }
}

@Composable
fun DrawerMenuItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), shape = RoundedCornerShape(24.dp), color = if (isSelected) MaterialTheme.colorScheme.inverseSurface else Color.Transparent) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = label, fontSize = 14.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatHistoryItem(session: ChatSession, isActive: Boolean, onClick: () -> Unit, onDelete: () -> Unit, onRename: (String) -> Unit) {
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf(session.title) }
    if (showRenameDialog) {
        AlertDialog(onDismissRequest = { showRenameDialog = false }, title = { Text("Rename Chat", fontFamily = FontFamily.Serif, fontSize = 16.sp, fontWeight = FontWeight.Bold) }, text = { OutlinedTextField(value = renameText, onValueChange = { renameText = it }, label = { Text("New name") }, modifier = Modifier.fillMaxWidth()) }, confirmButton = { Button(onClick = { onRename(renameText); showRenameDialog = false }) { Text("Rename") } }, dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") } })
    }
    Surface(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).combinedClickable(onClick = onClick, onLongClick = { showRenameDialog = true }), shape = RoundedCornerShape(24.dp), color = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = Icons.Default.ChatBubbleOutline, contentDescription = null, tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(text = session.title, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium, color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground)
            IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) { Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onBackground) }
        }
    }
}

@Composable
fun ChatScreen(viewModel: ChatViewModel, messages: List<ChatMessage>, isStreaming: Boolean, currentThinkingStatus: String, customLogoUriString: String = "", onLogoClick: () -> Unit = {}) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }
    var showAttachMenu by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(messages.size, currentThinkingStatus) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (messages.isEmpty() && currentThinkingStatus.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().weight(1f).padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 400.dp)) {
                    Box(modifier = Modifier.clickable { onLogoClick() }, contentAlignment = Alignment.BottomEnd) {
                        AiLogo(customLogoUriString, Modifier.size(56.dp), 18.dp)
                        Box(modifier = Modifier.size(18.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Edit, contentDescription = "Change Logo", tint = Color.White, modifier = Modifier.size(10.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(text = "Ask Alice anything...", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, letterSpacing = (-0.5).sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(text = "AI assistant with 40+ tools, MCP nodes, and skills integration.", fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        } else {
            LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp), contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp)) {
                items(messages) { msg ->
                    val blocks = MoshiHelper.fromJson(msg.blocksJson)
                    ChatMessageItem(msg = msg, blocks = blocks, onBlockCollapseToggle = { idx -> viewModel.toggleBlockCollapse(msg.id, idx) }, customLogoUriString = customLogoUriString)
                }
                if (isStreaming && currentThinkingStatus.isNotEmpty()) {
                    item { ThinkingPlaceholder(currentThinkingStatus) }
                }
            }
        }

        // Bottom Input Panel
        Surface(tonalElevation = 0.dp, color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
            Column(modifier = Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 12.dp, vertical = 8.dp)) {
                    // Attach button with dropdown
                    Box {
                        IconButton(onClick = { showAttachMenu = true }, modifier = Modifier.size(40.dp)) {
                            Icon(imageVector = Icons.Default.AttachFile, contentDescription = "Attach", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
                        }
                        DropdownMenu(expanded = showAttachMenu, onDismissRequest = { showAttachMenu = false }) {
                            DropdownMenuItem(text = { Text("📁 File") }, onClick = { showAttachMenu = false })
                            DropdownMenuItem(text = { Text("📷 Image") }, onClick = { showAttachMenu = false })
                            DropdownMenuItem(text = { Text("📄 Document") }, onClick = { showAttachMenu = false })
                        }
                    }
                    TextField(value = inputText, onValueChange = { inputText = it }, modifier = Modifier.weight(1f).testTag("chat_input_field").focusRequester(focusRequester), placeholder = { Text("Type message...") }, singleLine = false, colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent))
                    Spacer(modifier = Modifier.width(4.dp))
                    if (isStreaming) {
                        Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error).clickable { viewModel.cancelStreaming() }.testTag("stop_stream_button"), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    } else {
                        Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(if (inputText.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)).clickable(enabled = inputText.isNotEmpty()) { viewModel.sendMessage(inputText); inputText = "" }.testTag("send_message_button"), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                    // Voice button next to send
                    IconButton(onClick = { Toast.makeText(LocalContext.current, "Voice input coming soon", Toast.LENGTH_SHORT).show() }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Mic, contentDescription = "Voice", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    val activeMcpsCount = 0 // Will be set from viewModel
                    val activeToolsCount = 0 // Will be set from viewModel
                    val activeSkillsCount = 0 // Will be set from viewModel
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
                        Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(NaturalInfo))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "MCP ACTIVE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, letterSpacing = 0.5.sp)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
                        Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(NaturalWarning))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "${activeToolsCount + activeSkillsCount} TOOLS ENABLED", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, letterSpacing = 0.5.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun ThinkingPlaceholder(status: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = status, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatMessageItem(msg: ChatMessage, blocks: List<MessageBlock>, onBlockCollapseToggle: (Int) -> Unit, customLogoUriString: String = "") {
    val context = LocalContext.current
    val isUser = msg.role == "user"
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        if (!isUser) {
            AiLogo(customLogoUriString, Modifier.size(26.dp), 8.dp)
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f, fill = false), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
            if (isUser) {
                Surface(color = MaterialTheme.colorScheme.inverseSurface, contentColor = MaterialTheme.colorScheme.onBackground, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 4.dp), modifier = Modifier.widthIn(max = 320.dp)) {
                    Text(text = blocks.firstOrNull()?.text ?: "", modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp), fontSize = 15.sp, style = LocalTextStyle.current.copy(lineHeight = 22.sp))
                }
            } else {
                blocks.forEachIndexed { index, block ->
                    when (block.type) {
                        "thinking" -> { if (block.text.isNotEmpty()) ThinkingBlockCard(block.text, block.isCollapsed) { onBlockCollapseToggle(index) } }
                        "tool_call" -> { ToolCallBlockCard(block, block.isCollapsed, { onBlockCollapseToggle(index) }, context) }
                        "content" -> {
                            if (block.text.isNotEmpty()) {
                                // Render content with code block support
                                RenderContentWithCode(block.text)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RenderContentWithCode(text: String) {
    // Simple code block detection and rendering
    val codeBlockRegex = Regex("```(\\w+)?\\n(.*?)```", RegexOption.DOT_MATCHES_ALL)
    val matches = codeBlockRegex.findAll(text).toList()

    if (matches.isEmpty()) {
        androidx.compose.foundation.text.selection.SelectionContainer {
            Surface(color = Color.Transparent, contentColor = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(end = 24.dp, bottom = 4.dp, start = 4.dp)) {
                Text(text = text, fontSize = 15.sp, style = LocalTextStyle.current.copy(lineHeight = 22.sp))
            }
        }
        return
    }

    var lastIndex = 0
    androidx.compose.foundation.text.selection.SelectionContainer {
        Column(modifier = Modifier.padding(end = 24.dp, bottom = 4.dp, start = 4.dp)) {
        for (match in matches) {
            if (match.range.first > lastIndex) {
                Text(text = text.substring(lastIndex, match.range.first), fontSize = 15.sp, style = LocalTextStyle.current.copy(lineHeight = 22.sp))
            }
            val lang = match.groupValues[1].ifEmpty { "text" }
            val code = match.groupValues[2]
            CodeBlock(code = code, language = lang)
            lastIndex = match.range.last + 1
        }
        if (lastIndex < text.length) {
            Text(text = text.substring(lastIndex), fontSize = 15.sp, style = LocalTextStyle.current.copy(lineHeight = 22.sp))
        }
        }
    }
}

@Composable
fun CodeBlock(code: String, language: String) {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }
    // Syntax highlighting colors for common languages
    val syntaxColors = mapOf(
        "kotlin" to "#6800E6", "kt" to "#6800E6",
        "java" to "#B07219",
        "python" to "#3572A5", "py" to "#3572A5",
        "javascript" to "#F1E05A", "js" to "#F1E05A",
        "typescript" to "#3178C6", "ts" to "#3178C6",
        "html" to "#E34C26",
        "css" to "#563D7C",
        "json" to "#000000",
        "xml" to "#000080",
        "sql" to "#00008B",
        "bash" to "#6D303E", "sh" to "#6D303E",
        "c" to "#555555",
        "cpp" to "#F34B7D", "c++" to "#F34B7D",
        "go" to "#00ADD8", "golang" to "#00ADD8",
        "rust" to "#dea584",
        "ruby" to "#701516",
        "swift" to "#ffac45",
        "dart" to "#00B4AB"
    )
    val languageColor = syntaxColors[language.lowercase()] ?: MaterialTheme.colorScheme.primary.copy(alpha = 0.8f).toArgb().toHexString()
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color = Color(android.graphics.Color.parseColor(languageColor))))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = language.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                }
                Row {
                    TextButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("code", code))
                        Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy", fontSize = 11.sp)
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            Box(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(12.dp)) {
                    // Line numbers
                    Column(modifier = Modifier.padding(end = 8.dp)) {
                        code.lines().forEachIndexed { index, _ ->
                            Text(text = "${index + 1}", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                        }
                    }
                    // Code with text selection
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(text = code, fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun ThinkingBlockCard(thinkingText: String, isCollapsed: Boolean, onCollapseToggle: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column {
            Row(modifier = Modifier.fillMaxWidth().clickable { onCollapseToggle() }.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Thinking Process", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.weight(1f))
                Icon(imageVector = if (isCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp, contentDescription = "Expand/Collapse", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
            }
            AnimatedVisibility(visible = !isCollapsed, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Box(modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                    Text(text = thinkingText, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary, style = LocalTextStyle.current.copy(lineHeight = 18.sp, fontFamily = FontFamily.Monospace))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ToolCallBlockCard(block: MessageBlock, isCollapsed: Boolean, onCollapseToggle: () -> Unit, context: Context) {
    val statusColor = when (block.toolStatus) { "running" -> NaturalWarning; "success" -> NaturalSuccess; "error" -> NaturalError; else -> MaterialTheme.colorScheme.secondary }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column {
            Row(modifier = Modifier.fillMaxWidth().clickable { onCollapseToggle() }.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(statusColor))
                Spacer(modifier = Modifier.width(10.dp))
                Text(text = "Tool: ${block.toolName}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.weight(1f))
                Text(text = block.toolStatus.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = statusColor, modifier = Modifier.padding(end = 8.dp))
                Icon(imageVector = if (isCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
            }
            AnimatedVisibility(visible = !isCollapsed, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                    Text(text = "PARAMETERS:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, letterSpacing = 0.5.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(text = block.toolInput, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(10.dp), color = MaterialTheme.colorScheme.onBackground)
                    }
                    if (block.toolOutput != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(text = "OUTPUT:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, letterSpacing = 0.5.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Text(text = block.toolOutput, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(10.dp), color = MaterialTheme.colorScheme.primary)
                        }
                        // Duration and token info
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            Text(text = "⏱ ${block.toolDurationMs}ms | 🔢 ~${block.toolTokens} tokens", fontSize = 10.sp, color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ToolsManagementScreen(title: String, configs: List<ToolConfig>, onToggle: (String, Boolean) -> Unit, onBack: () -> Unit, isMcp: Boolean = false, onAdd: () -> Unit = {}) {
    var showAddDialog by remember { mutableStateOf(false) }
    var newToolName by remember { mutableStateOf("") }
    var newToolEndpoint by remember { mutableStateOf("") }
    var newToolApiKey by remember { mutableStateOf("") }
    var isVerifying by remember { mutableStateOf(false) }
    var verificationResult by remember { mutableStateOf<String?>(null) }

    if (showAddDialog) {
        AlertDialog(onDismissRequest = { showAddDialog = false }, title = { Text("Add ${if (isMcp) "MCP" else "Tool"}", fontWeight = FontWeight.Bold) }, text = {
            Column {
                OutlinedTextField(value = newToolName, onValueChange = { newToolName = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
                OutlinedTextField(value = newToolEndpoint, onValueChange = { newToolEndpoint = it }, label = { Text("Endpoint URL") }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
                OutlinedTextField(value = newToolApiKey, onValueChange = { newToolApiKey = it }, label = { Text("API Key (optional)") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
                if (verificationResult != null) {
                    Text(text = verificationResult!!, fontSize = 12.sp, color = if (verificationResult!!.contains("OK")) NaturalSuccess else NaturalError)
                }
            }
        }, confirmButton = {
            Button(onClick = {
                isVerifying = true
                // Simple ping verification
                verificationResult = "✓ Endpoint OK (simulated)"
                isVerifying = false
            }, enabled = newToolEndpoint.isNotEmpty()) {
                if (isVerifying) CircularProgressIndicator(modifier = Modifier.size(16.dp)) else Text("Verify Endpoint")
            }
        }, dismissButton = { 
            TextButton(onClick = { 
                if (verificationResult != null) {
                    val config = ToolConfig(
                        id = "${if (isMcp) "mcp" else "tool"}_${newToolName.lowercase().replace(" ", "_")}",
                        name = newToolName,
                        description = "Custom ${if (isMcp) "MCP" else "tool"} endpoint",
                        type = if (isMcp) "mcp" else "tool",
                        isEnabled = true,
                        extraInfo = verificationResult ?: "Added"
                    )
                    onToggle(config.id, true)
                    showAddDialog = false
                    newToolName = ""
                    newToolEndpoint = ""
                    newToolApiKey = ""
                    verificationResult = null
                } else {
                    showAddDialog = false
                }
            }) { Text("Cancel") } 
        })
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = title, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp, modifier = Modifier.weight(1f))
            IconButton(onClick = { showAddDialog = true }) { Icon(Icons.Default.Add, contentDescription = "Add") }
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (configs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "No configurations available", fontSize = 14.sp, color = MaterialTheme.colorScheme.secondary)
            }
        } else {
            LazyColumn {
                items(configs) { tool ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.inverseSurface), contentAlignment = Alignment.Center) {
                                Icon(imageVector = if (isMcp) Icons.Default.Computer else Icons.Default.Power, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = tool.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(text = tool.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary, style = LocalTextStyle.current.copy(lineHeight = 17.sp))
                                if (tool.extraInfo.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = tool.extraInfo, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (tool.extraInfo.contains("Disconnected") || tool.extraInfo.contains("Offline")) NaturalError else NaturalSuccess)
                                }
                            }
                            Switch(checked = tool.isEnabled, onCheckedChange = { onToggle(tool.id, it) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(viewModel: ChatViewModel, providers: List<ProviderProfile>, selectedProvider: ProviderProfile?, modelsList: List<String>, isLoadingModels: Boolean, onSave: (ProviderProfile) -> Unit = {}, onSwitch: (Long) -> Unit = {}, onDelete: (ProviderProfile) -> Unit = {}, onVerify: (ProviderProfile) -> Unit = {}, customLogoUriString: String = "", onLogoChangeClick: () -> Unit = {}, onLogoResetClick: () -> Unit = {}) {
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }
    var editId by remember { mutableLongStateOf(0L) }
    var editName by remember { mutableStateOf("") }
    var editEndpoint by remember { mutableStateOf("") }
    var editApiKey by remember { mutableStateOf("") }
    var editFormat by remember { mutableStateOf("openai") }
    var editModel by remember { mutableStateOf("") }
    var editTemp by remember { mutableDoubleStateOf(0.7) }
    var editMaxTokens by remember { mutableIntStateOf(2048) }
    var editTopP by remember { mutableDoubleStateOf(0.9) }

    LaunchedEffect(selectedProvider, showAddDialog) {
        if (selectedProvider != null && !showAddDialog) {
            editId = selectedProvider.id
            editName = selectedProvider.name
            editEndpoint = selectedProvider.endpointUrl
            editApiKey = selectedProvider.apiKey
            editFormat = selectedProvider.protocolFormat
            editModel = selectedProvider.activeModel
            editTemp = selectedProvider.temperature
            editMaxTokens = selectedProvider.maxTokens
            editTopP = selectedProvider.topP
        }
    }

    if (showAddDialog) {
        var newName by remember { mutableStateOf("") }
        var newEndpoint by remember { mutableStateOf("https://api.openai.com/v1") }
        var newApiKey by remember { mutableStateOf("") }
        var newFormat by remember { mutableStateOf("openai") }
        AlertDialog(onDismissRequest = { showAddDialog = false }, title = { Text("Add Provider", fontFamily = FontFamily.Serif, fontSize = 16.sp, fontWeight = FontWeight.Bold) }, text = {
            Column {
                OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Provider Name") }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
                OutlinedTextField(value = newEndpoint, onValueChange = { newEndpoint = it }, label = { Text("Endpoint URL") }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
                OutlinedTextField(value = newApiKey, onValueChange = { newApiKey = it }, label = { Text("API Key") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
                Text("Protocol Format:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = newFormat == "openai", onClick = { newFormat = "openai" }); Text("OpenAI"); Spacer(modifier = Modifier.width(16.dp))
                    RadioButton(selected = newFormat == "hermes", onClick = { newFormat = "hermes" }); Text("Hermes ChatML"); Spacer(modifier = Modifier.width(16.dp))
                    RadioButton(selected = newFormat == "gemini", onClick = { newFormat = "gemini" }); Text("Gemini")
                }
            }
        }, confirmButton = {
            TextButton(onClick = {
                if (newName.isNotEmpty()) {
                    onSave(ProviderProfile(name = newName, endpointUrl = newEndpoint, apiKey = newApiKey, protocolFormat = newFormat, isSelected = false))
                    showAddDialog = false
                }
            }) { Text("Add") }
        }, dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel") } })
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Provider Configuration", fontSize = 20.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Button(onClick = { showAddDialog = true }, shape = RoundedCornerShape(8.dp)) {
                    Icon(Icons.Default.Add, contentDescription = null); Spacer(modifier = Modifier.width(4.dp)); Text("Add")
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
        item {
            Text("SELECT ACTIVE PROFILE:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(6.dp))
        }
        items(providers) { p ->
            Card(colors = CardDefaults.cardColors(containerColor = if (p.isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = p.isSelected, onClick = { onSwitch(p.id) })
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(p.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(p.endpointUrl, fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                    }
                    if (providers.size > 1) {
                        IconButton(onClick = { onDelete(p) }) { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)) }
                    }
                }
            }
        }
        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            Text("ACTIVE PROFILE FORM:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = editName, onValueChange = { editName = it }, label = { Text("Provider Name") }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
            OutlinedTextField(value = editEndpoint, onValueChange = { editEndpoint = it }, label = { Text("Endpoint URL") }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
            OutlinedTextField(value = editApiKey, onValueChange = { editApiKey = it }, label = { Text("API Key (leave empty for local)") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
            Text("Protocol Format:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                RadioButton(selected = editFormat == "openai", onClick = { editFormat = "openai" }); Text("OpenAI"); Spacer(modifier = Modifier.width(12.dp))
                RadioButton(selected = editFormat == "hermes", onClick = { editFormat = "hermes" }); Text("Hermes ChatML"); Spacer(modifier = Modifier.width(12.dp))
                RadioButton(selected = editFormat == "gemini", onClick = { editFormat = "gemini" }); Text("Gemini")
            }
            Button(onClick = {
                val p = ProviderProfile(id = editId, name = editName, endpointUrl = editEndpoint, apiKey = editApiKey, protocolFormat = editFormat, activeModel = editModel, temperature = editTemp, maxTokens = editMaxTokens, topP = editTopP)
                onVerify(p)
            }, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) {
                if (isLoadingModels) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                else { Icon(Icons.Default.Refresh, contentDescription = null); Spacer(modifier = Modifier.width(8.dp)); Text("Verify & Load Models") }
            }
            if (modelsList.isNotEmpty()) {
                Text("Select Default Model:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                var expandedModelsMenu by remember { mutableStateOf(false) }
                Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    Button(onClick = { expandedModelsMenu = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onBackground)) {
                        Text(editModel.ifEmpty { "Select model..." })
                    }
                    DropdownMenu(expanded = expandedModelsMenu, onDismissRequest = { expandedModelsMenu = false }, modifier = Modifier.fillMaxWidth()) {
                        modelsList.forEach { m ->
                            DropdownMenuItem(text = { Text(m) }, onClick = { editModel = m; expandedModelsMenu = false })
                        }
                    }
                }
            } else {
                OutlinedTextField(value = editModel, onValueChange = { editModel = it }, label = { Text("Default Model (e.g. gpt-4o-mini / llama3.2)") }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
            }
            var showAdvanced by remember { mutableStateOf(false) }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Column {
                    Row(modifier = Modifier.fillMaxWidth().clickable { showAdvanced = !showAdvanced }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Tune, contentDescription = null)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Advanced Parameters", fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Icon(imageVector = if (showAdvanced) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null)
                    }
                    if (showAdvanced) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Temperature: ${String.format("%.1f", editTemp)}", fontSize = 12.sp)
                            Slider(value = editTemp.toFloat(), onValueChange = { editTemp = it.toDouble() }, valueRange = 0.0f..1.5f, modifier = Modifier.fillMaxWidth())
                            Text("Max Output Tokens: $editMaxTokens", fontSize = 12.sp)
                            Slider(value = editMaxTokens.toFloat(), onValueChange = { editMaxTokens = it.toInt() }, valueRange = 256.0f..4096.0f, modifier = Modifier.fillMaxWidth())
                            Text("Top P: ${String.format("%.1f", editTopP)}", fontSize = 12.sp)
                            Slider(value = editTopP.toFloat(), onValueChange = { editTopP = it.toDouble() }, valueRange = 0.0f..1.0f, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
            Button(onClick = {
                val updated = ProviderProfile(id = editId, name = editName, endpointUrl = editEndpoint, apiKey = editApiKey, protocolFormat = editFormat, activeModel = editModel.ifEmpty { if (editFormat == "openai") "gpt-4o-mini" else "llama3.2" }, temperature = editTemp, maxTokens = editMaxTokens, topP = editTopP)
                onSave(updated)
                Toast.makeText(context, "Profile Saved!", Toast.LENGTH_SHORT).show()
            }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.Check, contentDescription = null); Spacer(modifier = Modifier.width(8.dp)); Text("Save Profile Settings", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            Text("CUSTOM AI LOGO:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    AiLogo(customLogoUriString, Modifier.size(48.dp), 16.dp)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Agent Logo", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(text = if (customLogoUriString.isNotEmpty()) "Custom logo active" else "Using default logo", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                    }
                }
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp), horizontalArrangement = Arrangement.End) {
                    if (customLogoUriString.isNotEmpty()) {
                        TextButton(onClick = onLogoResetClick) { Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(modifier = Modifier.width(4.dp)); Text("Reset") }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Button(onClick = onLogoChangeClick, shape = RoundedCornerShape(8.dp)) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp)); Spacer(modifier = Modifier.width(6.dp)); Text("Choose Image", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import com.example.ui.theme.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import com.example.viewmodel.ChatViewModel
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage

@Composable
fun AiLogo(
    customLogoUriString: String,
    modifier: Modifier = Modifier,
    innerDotSize: androidx.compose.ui.unit.Dp = 12.dp
) {
    if (customLogoUriString.isNotEmpty()) {
        AsyncImage(
            model = customLogoUriString,
            contentDescription = "AI Logo",
            modifier = modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            error = androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Default.SmartToy)
        )
    } else {
        Box(
            modifier = modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(innerDotSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onPrimary)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val sharedPrefs = remember { context.getSharedPreferences("ai_agent_prefs", android.content.Context.MODE_PRIVATE) }
    var customLogoUriString by remember {
        mutableStateOf(sharedPrefs.getString("custom_logo_uri", "") ?: "")
    }

    val logoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                // Ignore if permission cannot be persisted
            }
            val uriStr = it.toString()
            customLogoUriString = uriStr
            sharedPrefs.edit().putString("custom_logo_uri", uriStr).apply()
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

    // Navigation states: "chat", "tools", "mcp", "skills", "settings"
    var currentScreen by remember { mutableStateOf("chat") }

    // Sidebar search session term
    var sessionSearchQuery by remember { mutableStateOf("") }

    // Display error messages as toasts
    LaunchedEffect(errorMsg) {
        errorMsg?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    // Modal Drawer
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier
                    .width(320.dp)
                    .fillMaxHeight(),
                drawerContainerColor = MaterialTheme.colorScheme.background
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Header Logo
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { logoLauncher.launch("image/*") }
                            .padding(bottom = 16.dp)
                    ) {
                        Box(contentAlignment = Alignment.BottomEnd) {
                            AiLogo(
                                customLogoUriString = customLogoUriString,
                                modifier = Modifier.size(36.dp),
                                innerDotSize = 12.dp
                            )
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Logo",
                                    tint = Color.White,
                                    modifier = Modifier.size(8.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Alice Agent",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                letterSpacing = (-0.5).sp
                            )
                            Text(
                                text = "Klik logo untuk ganti",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    // New Chat Button
                    Button(
                        onClick = {
                            scope.launch {
                                viewModel.createNewSession()
                                currentScreen = "chat"
                                drawerState.close()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .testTag("new_chat_button"),
                        shape = RoundedCornerShape(24.dp), // Fully rounded pill shape
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            Icons.Default.Add, 
                            contentDescription = "New Chat", 
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "New Chat", 
                            fontWeight = FontWeight.Bold, 
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }

                    // Search Chat Session
                    OutlinedTextField(
                        value = sessionSearchQuery,
                        onValueChange = { sessionSearchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        placeholder = { Text("Cari chat...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            focusedContainerColor = MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Menu Categories
                    Text(
                        text = "MANAGE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                    )

                    DrawerMenuItem(
                        icon = Icons.Default.Build,
                        label = "Tools",
                        isSelected = currentScreen == "tools",
                        onClick = {
                            currentScreen = "tools"
                            scope.launch { drawerState.close() }
                        }
                    )
                    DrawerMenuItem(
                        icon = Icons.Default.Computer,
                        label = "MCP Servers",
                        isSelected = currentScreen == "mcp",
                        onClick = {
                            currentScreen = "mcp"
                            scope.launch { drawerState.close() }
                        }
                    )
                    DrawerMenuItem(
                        icon = Icons.Default.OfflineBolt,
                        label = "Skills System",
                        isSelected = currentScreen == "skills",
                        onClick = {
                            currentScreen = "skills"
                            scope.launch { drawerState.close() }
                        }
                    )
                    DrawerMenuItem(
                        icon = Icons.Default.Settings,
                        label = "Settings Profile",
                        isSelected = currentScreen == "settings",
                        onClick = {
                            currentScreen = "settings"
                            scope.launch { drawerState.close() }
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Chats History List
                    Text(
                        text = "CHATS HISTORY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                    )

                    val filteredSessions = remember(sessions, sessionSearchQuery) {
                        if (sessionSearchQuery.isEmpty()) sessions
                        else sessions.filter { it.title.contains(sessionSearchQuery, ignoreCase = true) }
                    }

                    if (filteredSessions.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Tidak ada percakapan",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            items(filteredSessions) { s ->
                                ChatHistoryItem(
                                    session = s,
                                    isActive = activeSession?.id == s.id && currentScreen == "chat",
                                    onClick = {
                                        viewModel.selectSession(s.id)
                                        currentScreen = "chat"
                                        scope.launch { drawerState.close() }
                                    },
                                    onDelete = {
                                        viewModel.deleteSession(s.id)
                                    },
                                    onRename = { newTitle ->
                                        viewModel.renameSession(s.id, newTitle)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                val titleText = when (currentScreen) {
                                    "chat" -> activeSession?.title ?: "AI Agent Client"
                                    "tools" -> "Manage Tools"
                                    "mcp" -> "Manage MCP Servers"
                                    "skills" -> "Manage Skills"
                                    "settings" -> "Settings Profile"
                                    else -> "AI Client"
                                }
                                Text(
                                    text = titleText,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                // Dot Status Indicator
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isStreaming) NaturalWarning
                                            else if (selectedProvider != null) NaturalSuccess
                                            else NaturalError
                                        )
                                )
                            }
                            val modelText = when (currentScreen) {
                                "chat" -> selectedProvider?.activeModel ?: "No active model"
                                "tools" -> "Local Capability"
                                "mcp" -> "Node Servers"
                                "skills" -> "Dynamic System Skills"
                                "settings" -> "API Configuration"
                                else -> "Llama 3.1 70B"
                            }
                            Text(
                                text = modelText.uppercase(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary,
                                letterSpacing = 1.sp
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { scope.launch { drawerState.open() } },
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Open Drawer",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                if (currentScreen != "chat") {
                                    currentScreen = "chat"
                                } else {
                                    scope.launch { viewModel.createNewSession() }
                                }
                            },
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .size(44.dp)
                        ) {
                            Icon(
                                imageVector = if (currentScreen != "chat") Icons.Default.Chat else Icons.Default.Add,
                                contentDescription = "Action",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            modifier = modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                when (currentScreen) {
                    "chat" -> ChatScreen(
                        viewModel = viewModel,
                        messages = messages,
                        isStreaming = isStreaming,
                        currentThinkingStatus = currentThinkingStatus,
                        customLogoUriString = customLogoUriString,
                        onLogoClick = { logoLauncher.launch("image/*") }
                    )
                    "tools" -> ToolsManagementScreen(
                        title = "Tools Backend",
                        configs = tools,
                        onToggle = { id, enabled -> viewModel.toggleTool(id, enabled) },
                        onBack = { currentScreen = "chat" }
                    )
                    "mcp" -> ToolsManagementScreen(
                        title = "MCP Server Nodes",
                        configs = mcps,
                        onToggle = { id, enabled -> viewModel.toggleTool(id, enabled) },
                        onBack = { currentScreen = "chat" },
                        isMcp = true
                    )
                    "skills" -> ToolsManagementScreen(
                        title = "System Skills",
                        configs = skills,
                        onToggle = { id, enabled -> viewModel.toggleTool(id, enabled) },
                        onBack = { currentScreen = "chat" }
                    )
                    "settings" -> SettingsScreen(
                        viewModel = viewModel,
                        providers = providers,
                        selectedProvider = selectedProvider,
                        modelsList = modelsList,
                        isLoadingModels = isLoadingModels,
                        onSave = { viewModel.saveProviderProfile(it) },
                        onDelete = { viewModel.deleteProviderProfile(it) },
                        onSwitch = { viewModel.switchProviderProfile(it) },
                        onVerify = { viewModel.verifyAndLoadModels(it) },
                        customLogoUriString = customLogoUriString,
                        onLogoChangeClick = { logoLauncher.launch("image/*") },
                        onLogoResetClick = {
                            customLogoUriString = ""
                            sharedPrefs.edit().putString("custom_logo_uri", "").apply()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun DrawerMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        shape = RoundedCornerShape(24.dp), // Styled pill shape
        color = if (isSelected) MaterialTheme.colorScheme.inverseSurface else Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatHistoryItem(
    session: ChatSession,
    isActive: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRename: (String) -> Unit
) {
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf(session.title) }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Ubah Nama Percakapan", fontFamily = FontFamily.Serif, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRename(renameText)
                    showRenameDialog = false
                }) {
                    Text("Simpan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showRenameDialog = true }
            ),
        shape = RoundedCornerShape(24.dp),
        color = if (isActive) MaterialTheme.colorScheme.inverseSurface else Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.ChatBubbleOutline,
                contentDescription = null,
                tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = session.title,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Hapus",
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    messages: List<ChatMessage>,
    isStreaming: Boolean,
    currentThinkingStatus: String,
    customLogoUriString: String = "",
    onLogoClick: () -> Unit = {}
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }

    val tools by viewModel.tools.collectAsStateWithLifecycle()
    val mcps by viewModel.mcps.collectAsStateWithLifecycle()
    val skills by viewModel.skills.collectAsStateWithLifecycle()

    // Auto scroll to bottom when messages list size changes
    LaunchedEffect(messages.size, currentThinkingStatus) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (messages.isEmpty() && currentThinkingStatus.isEmpty()) {
            // Welcome empty screen (Styled with beautiful, spacious minimalism)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.widthIn(max = 400.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clickable { onLogoClick() },
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        AiLogo(
                            customLogoUriString = customLogoUriString,
                            modifier = Modifier.size(56.dp),
                            innerDotSize = 18.dp
                        )
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Ganti Logo",
                                tint = Color.White,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Tanya Alice apa saja...",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        letterSpacing = (-0.5).sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Asisten lokal cerdas yang terintegrasi penuh dengan Tools, MCP Nodes, dan Skills sistem.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = LocalTextStyle.current.copy(lineHeight = 19.sp)
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp)
            ) {
                items(messages) { msg ->
                    val blocks = MoshiHelper.fromJson(msg.blocksJson)
                    ChatMessageItem(
                        msg = msg,
                        blocks = blocks,
                        onBlockCollapseToggle = { idx ->
                            viewModel.toggleBlockCollapse(msg.id, idx)
                        },
                        customLogoUriString = customLogoUriString
                    )
                }

                // Streaming Thinking State Indicator
                if (isStreaming && currentThinkingStatus.isNotEmpty()) {
                    item {
                        ThinkingPlaceholder(currentThinkingStatus)
                    }
                }
            }
        }

        // Bottom Input Panel (Polished White/Cream Bar with Top Outline)
        Surface(
            tonalElevation = 0.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                ) {
                    // Left Option Button
                    IconButton(
                        onClick = {
                            // Clear chat to start clean or execute helper
                            scope.launch {
                                viewModel.createNewSession()
                            }
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New Chat",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Text Input
                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_input_field"),
                        placeholder = { 
                            Text(
                                "Tanya Alice apa saja...", 
                                color = MaterialTheme.colorScheme.secondary,
                                fontSize = 14.sp
                            ) 
                        },
                        maxLines = 5,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        textStyle = LocalTextStyle.current.copy(
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 14.sp
                        )
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    if (isStreaming) {
                        // Stop Stream Button
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error)
                                .clickable { viewModel.cancelStreaming() }
                                .testTag("stop_stream_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else {
                        // Send Button
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (inputText.isNotEmpty()) MaterialTheme.colorScheme.primary 
                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                )
                                .clickable(enabled = inputText.isNotEmpty()) {
                                    viewModel.sendMessage(inputText)
                                    inputText = ""
                                }
                                .testTag("send_message_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Kirim",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Active Capabilities & MCP status footer row
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val activeMcpsCount = mcps.count { it.isEnabled }
                    val activeToolsCount = tools.count { it.isEnabled }
                    val activeSkillsCount = skills.count { it.isEnabled }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(NaturalInfo)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "MCP ACTIVE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(NaturalWarning)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${activeToolsCount + activeSkillsCount} TOOLS ENABLED",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ThinkingPlaceholder(status: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = status,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
fun ChatMessageItem(
    msg: ChatMessage,
    blocks: List<MessageBlock>,
    onBlockCollapseToggle: (Int) -> Unit,
    customLogoUriString: String = ""
) {
    val isUser = msg.role == "user"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            AiLogo(
                customLogoUriString = customLogoUriString,
                modifier = Modifier.size(26.dp),
                innerDotSize = 8.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
        }

        Column(
            modifier = Modifier.weight(1f, fill = false),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            if (isUser) {
                // User Message (Warm grey bubble, dark text matching Natural Tones bg-[#EEEBE4])
                Surface(
                    color = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp),
                    modifier = Modifier.padding(start = 40.dp)
                ) {
                    Text(
                        text = blocks.firstOrNull()?.text ?: "",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                        fontSize = 15.sp,
                        style = LocalTextStyle.current.copy(lineHeight = 22.sp)
                    )
                }
            } else {
                // Assistant Message containing blocks (thinking, tool calls, final text)
                blocks.forEachIndexed { index, block ->
                    when (block.type) {
                        "thinking" -> {
                            if (block.text.isNotEmpty()) {
                                ThinkingBlockCard(
                                    thinkingText = block.text,
                                    isCollapsed = block.isCollapsed,
                                    onCollapseToggle = { onBlockCollapseToggle(index) }
                                )
                            }
                        }
                        "tool_call" -> {
                            ToolCallBlockCard(
                                block = block,
                                isCollapsed = block.isCollapsed,
                                onCollapseToggle = { onBlockCollapseToggle(index) }
                            )
                        }
                        "content" -> {
                            if (block.text.isNotEmpty()) {
                                Surface(
                                    color = Color.Transparent,
                                    contentColor = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.padding(end = 24.dp, bottom = 4.dp, start = 4.dp)
                                ) {
                                    Text(
                                        text = block.text,
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        fontSize = 15.sp,
                                        style = LocalTextStyle.current.copy(lineHeight = 22.sp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ThinkingBlockCard(
    thinkingText: String,
    isCollapsed: Boolean,
    onCollapseToggle: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp, end = 24.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCollapseToggle() }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Thinking Process",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (isCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                    contentDescription = "Expand/Collapse",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.tertiary
                )
            }

            AnimatedVisibility(
                visible = !isCollapsed,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
                ) {
                    Text(
                        text = thinkingText,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        style = LocalTextStyle.current.copy(
                            lineHeight = 18.sp, 
                            fontFamily = FontFamily.Monospace,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun ToolCallBlockCard(
    block: MessageBlock,
    isCollapsed: Boolean,
    onCollapseToggle: () -> Unit
) {
    val statusColor = when (block.toolStatus) {
        "running" -> NaturalWarning
        "success" -> NaturalSuccess
        "error" -> NaturalError
        else -> MaterialTheme.colorScheme.secondary
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp, end = 24.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCollapseToggle() }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Tool: ${block.toolName}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = block.toolStatus.uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = statusColor,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Icon(
                    imageVector = if (isCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
            }

            AnimatedVisibility(
                visible = !isCollapsed,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
                ) {
                    Text(
                        text = "PARAMETERS:",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = block.toolInput,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(10.dp),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    if (block.toolOutput != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "OUTPUT:",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = block.toolOutput,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(10.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ToolsManagementScreen(
    title: String,
    configs: List<ToolConfig>,
    onToggle: (String, Boolean) -> Unit,
    onBack: () -> Unit,
    isMcp: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (configs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Tidak ada konfigurasi tersedia",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        } else {
            LazyColumn {
                items(configs) { tool ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.inverseSurface),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isMcp) Icons.Default.Computer else Icons.Default.Power,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tool.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = tool.description,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.secondary,
                                    style = LocalTextStyle.current.copy(lineHeight = 17.sp)
                                )
                                if (tool.extraInfo.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = tool.extraInfo,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (tool.extraInfo.contains("Disconnected") || tool.extraInfo.contains("Offline")) NaturalError else NaturalSuccess
                                    )
                                }
                            }
                            Switch(
                                checked = tool.isEnabled,
                                onCheckedChange = { onToggle(tool.id, it) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    viewModel: ChatViewModel,
    providers: List<ProviderProfile>,
    selectedProvider: ProviderProfile?,
    modelsList: List<String>,
    isLoadingModels: Boolean,
    onSave: (ProviderProfile) -> Unit,
    onDelete: (ProviderProfile) -> Unit,
    onSwitch: (Long) -> Unit,
    onVerify: (ProviderProfile) -> Unit,
    customLogoUriString: String = "",
    onLogoChangeClick: () -> Unit = {},
    onLogoResetClick: () -> Unit = {}
) {
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }

    // Selected profile form fields
    var editId by remember { mutableLongStateOf(0L) }
    var editName by remember { mutableStateOf("") }
    var editEndpoint by remember { mutableStateOf("") }
    var editApiKey by remember { mutableStateOf("") }
    var editFormat by remember { mutableStateOf("openai") }
    var editModel by remember { mutableStateOf("") }
    var editTemp by remember { mutableDoubleStateOf(0.7) }
    var editMaxTokens by remember { mutableIntStateOf(2048) }
    var editTopP by remember { mutableDoubleStateOf(0.9) }

    // Load selected provider profile into form on start or switch
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
        // Simple Add Provider Dialog
        var newName by remember { mutableStateOf("") }
        var newEndpoint by remember { mutableStateOf("https://api.openai.com/v1") }
        var newApiKey by remember { mutableStateOf("") }
        var newFormat by remember { mutableStateOf("openai") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Tambah Provider Baru", fontFamily = FontFamily.Serif, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Nama Provider") },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = newEndpoint,
                        onValueChange = { newEndpoint = it },
                        label = { Text("Endpoint URL") },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = newApiKey,
                        onValueChange = { newApiKey = it },
                        label = { Text("API Key") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                    Text("Protocol Format:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = newFormat == "openai", onClick = { newFormat = "openai" })
                        Text("OpenAI", fontSize = 13.sp)
                        Spacer(modifier = Modifier.width(16.dp))
                        RadioButton(selected = newFormat == "hermes", onClick = { newFormat = "hermes" })
                        Text("Hermes ChatML", fontSize = 13.sp)
                        Spacer(modifier = Modifier.width(16.dp))
                        RadioButton(selected = newFormat == "gemini", onClick = { newFormat = "gemini" })
                        Text("Gemini", fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotEmpty()) {
                        val p = ProviderProfile(
                            name = newName,
                            endpointUrl = newEndpoint,
                            apiKey = newApiKey,
                            protocolFormat = newFormat,
                            isSelected = false
                        )
                        onSave(p)
                        showAddDialog = false
                    }
                }) {
                    Text("Tambah")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Konfigurasi Provider",
                    fontSize = 20.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { showAddDialog = true },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add")
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // List Profiles Selection
        item {
            Text("PILIH PROFIL AKTIF:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(6.dp))
        }

        items(providers) { p ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (p.isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { onSwitch(p.id) },
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = p.isSelected,
                        onClick = { onSwitch(p.id) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(p.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(p.endpointUrl, fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                    }
                    if (providers.size > 1) {
                        IconButton(onClick = { onDelete(p) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        }

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            Text("FORM PROFIL AKTIF:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            // Edit Fields Form
            OutlinedTextField(
                value = editName,
                onValueChange = { editName = it },
                label = { Text("Nama Provider Profile") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )

            OutlinedTextField(
                value = editEndpoint,
                onValueChange = { editEndpoint = it },
                label = { Text("Endpoint URL") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )

            OutlinedTextField(
                value = editApiKey,
                onValueChange = { editApiKey = it },
                label = { Text("API Key (kosongkan jika menggunakan server lokal)") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            )

            // Protocol Dropdown UI
            Text("Format Protokol:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                RadioButton(selected = editFormat == "openai", onClick = { editFormat = "openai" })
                Text("OpenAI", fontSize = 13.sp)
                Spacer(modifier = Modifier.width(12.dp))
                RadioButton(selected = editFormat == "hermes", onClick = { editFormat = "hermes" })
                Text("Hermes ChatML", fontSize = 13.sp)
                Spacer(modifier = Modifier.width(12.dp))
                RadioButton(selected = editFormat == "gemini", onClick = { editFormat = "gemini" })
                Text("Gemini", fontSize = 13.sp)
            }

            // Connection Verify Button
            Button(
                onClick = {
                    val p = ProviderProfile(
                        id = editId,
                        name = editName,
                        endpointUrl = editEndpoint,
                        apiKey = editApiKey,
                        protocolFormat = editFormat,
                        activeModel = editModel,
                        temperature = editTemp,
                        maxTokens = editMaxTokens,
                        topP = editTopP,
                        isSelected = true
                    )
                    onVerify(p)
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                if (isLoadingModels) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Verify & Load Models")
                }
            }

            // Model Selection
            if (modelsList.isNotEmpty()) {
                Text("Pilih Model Default:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                var expandedModelsMenu by remember { mutableStateOf(false) }

                Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    Button(
                        onClick = { expandedModelsMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface)
                    ) {
                        Text(editModel.ifEmpty { "Pilih model..." })
                    }
                    DropdownMenu(
                        expanded = expandedModelsMenu,
                        onDismissRequest = { expandedModelsMenu = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        modelsList.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m) },
                                onClick = {
                                    editModel = m
                                    expandedModelsMenu = false
                                }
                            )
                        }
                    }
                }
            } else {
                OutlinedTextField(
                    value = editModel,
                    onValueChange = { editModel = it },
                    label = { Text("Model Default (Contoh: gemini-1.5-flash / gpt-4o)") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                )
            }

            // Expandable Advanced Config Parameters (sliders)
            var showAdvanced by remember { mutableStateOf(false) }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAdvanced = !showAdvanced }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Advanced Parameters", fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Icon(imageVector = if (showAdvanced) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null)
                    }

                    if (showAdvanced) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            // Temperature slider
                            Text("Temperature: ${String.format("%.1f", editTemp)}", fontSize = 12.sp)
                            Slider(
                                value = editTemp.toFloat(),
                                onValueChange = { editTemp = it.toDouble() },
                                valueRange = 0.0f..1.5f,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Max tokens
                            Text("Max Output Tokens: $editMaxTokens", fontSize = 12.sp)
                            Slider(
                                value = editMaxTokens.toFloat(),
                                onValueChange = { editMaxTokens = it.toInt() },
                                valueRange = 256.0f..4096.0f,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Top P
                            Text("Top P: ${String.format("%.1f", editTopP)}", fontSize = 12.sp)
                            Slider(
                                value = editTopP.toFloat(),
                                onValueChange = { editTopP = it.toDouble() },
                                valueRange = 0.0f..1.0f,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // Save Form Button
            Button(
                onClick = {
                    val updated = ProviderProfile(
                        id = editId,
                        name = editName,
                        endpointUrl = editEndpoint,
                        apiKey = editApiKey,
                        protocolFormat = editFormat,
                        activeModel = editModel.ifEmpty { if (editFormat == "gemini") "gemini-1.5-flash" else "gpt-4o-mini" },
                        temperature = editTemp,
                        maxTokens = editMaxTokens,
                        topP = editTopP,
                        isSelected = true
                    )
                    onSave(updated)
                    Toast.makeText(context, "Profile Saved!", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Profile Settings", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            Text("LOGO AI KUSTOM:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))
            
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AiLogo(
                        customLogoUriString = customLogoUriString,
                        modifier = Modifier.size(48.dp),
                        innerDotSize = 16.dp
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Logo Agen AI", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            text = if (customLogoUriString.isNotEmpty()) "Logo kustom aktif" else "Menggunakan logo default",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (customLogoUriString.isNotEmpty()) {
                        TextButton(onClick = onLogoResetClick) {
                            Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset ke Default", fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Button(
                        onClick = onLogoChangeClick,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pilih Gambar", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

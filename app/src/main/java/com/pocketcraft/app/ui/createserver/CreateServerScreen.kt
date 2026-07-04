package com.pocketcraft.app.ui.createserver

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocketcraft.app.data.LoaderType
import com.pocketcraft.app.ui.theme.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateServerScreen(
    onNavigateBack: () -> Unit,
    onServerCreated: (String) -> Unit,
    viewModel: CreateServerViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val createdId by viewModel.createdServerId.collectAsStateWithLifecycle()

    LaunchedEffect(createdId) {
        createdId?.let { id ->
            viewModel.consumeCreatedServerId()
            onServerCreated(id)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Server — Step ${state.step} of 5") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state.step > 1) viewModel.prevStep() else onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Step progress bar
            LinearProgressIndicator(
                progress = { state.step / 5f },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            AnimatedContent(
                targetState = state.step,
                transitionSpec = {
                    if (targetState > initialState) {
                        slideInHorizontally { it } + fadeIn() togetherWith
                            slideOutHorizontally { -it } + fadeOut()
                    } else {
                        slideInHorizontally { -it } + fadeIn() togetherWith
                            slideOutHorizontally { it } + fadeOut()
                    }
                },
                label = "wizardStep"
            ) { step ->
                when (step) {
                    1 -> NameStep(state, viewModel)
                    2 -> LoaderStep(state, viewModel)
                    3 -> VersionStep(state, viewModel)
                    4 -> RamStep(state, viewModel)
                    5 -> EulaStep(state, viewModel)
                }
            }
        }
    }
}

// ── Step 1: Name ──────────────────────────────────────────────────────────────

@Composable
private fun NameStep(state: CreateServerUiState, viewModel: CreateServerViewModel) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    StepScaffold(
        title = "Name your server",
        subtitle = "Pick something memorable. You can change it later.",
        onNext = viewModel::nextStep,
        nextEnabled = state.name.isNotBlank()
    ) {
        OutlinedTextField(
            value = state.name,
            onValueChange = viewModel::updateName,
            label = { Text("Server name") },
            isError = state.nameError != null,
            supportingText = {
                Text(state.nameError ?: "${state.name.length}/32")
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { viewModel.nextStep() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
        )
    }
}

// ── Step 2: Loader ────────────────────────────────────────────────────────────

@Composable
private fun LoaderStep(state: CreateServerUiState, viewModel: CreateServerViewModel) {
    StepScaffold(
        title = "Choose a server type",
        subtitle = "Paper is the recommended choice for most players — fast, stable, and widely supported.",
        onNext = viewModel::nextStep,
        nextEnabled = true
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LoaderOption(
                title = "Paper",
                description = "High-performance Bukkit fork. Supports plugins. Best for survival and mini-game servers.",
                selected = state.loaderType == LoaderType.PAPER,
                onClick = { viewModel.updateLoader(LoaderType.PAPER) }
            )
            // Fabric locked in Phase 1
            LoaderOption(
                title = "Fabric",
                description = "Lightweight modding platform. Coming in a future update.",
                selected = false,
                enabled = false,
                onClick = {}
            )
        }
    }
}

@Composable
private fun LoaderOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.outline
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, onClick = onClick),
        border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                             else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RadioButton(
                selected = selected,
                onClick = null,
                enabled = enabled
            )
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── Step 3: Version ───────────────────────────────────────────────────────────

@Composable
private fun VersionStep(state: CreateServerUiState, viewModel: CreateServerViewModel) {
    StepScaffold(
        title = "Minecraft version",
        subtitle = "Fetched live from the Paper API. Newer = more features; older = more plugin support.",
        onNext = viewModel::nextStep,
        nextEnabled = state.selectedVersion.isNotBlank()
    ) {
        when (val vs = state.versionState) {
            is VersionLoadState.Loading -> {
                repeat(6) {
                    VersionSkeleton()
                    Spacer(Modifier.height(8.dp))
                }
            }
            is VersionLoadState.Error -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CloudOff, contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    Text("Couldn't load versions: ${vs.message}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = viewModel::retryFetchVersions) {
                        Text("Retry")
                    }
                }
            }
            is VersionLoadState.Success -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(vs.versions) { version ->
                        val isSelected = version == state.selectedVersion
                        ListItem(
                            headlineContent = { Text(version) },
                            leadingContent = {
                                if (isSelected) {
                                    Icon(Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary)
                                } else {
                                    Icon(Icons.Default.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.outline)
                                }
                            },
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.updateVersion(version) },
                            colors = ListItemDefaults.colors(
                                containerColor = if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                else
                                    MaterialTheme.colorScheme.surface
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VersionSkeleton() {
    val infiniteTransition = rememberInfiniteTransition(label = "skeleton")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeletonAlpha"
    )
    Surface(
        modifier = Modifier.fillMaxWidth().height(44.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)
    ) {}
}

// ── Step 4: RAM ───────────────────────────────────────────────────────────────

@Composable
private fun RamStep(state: CreateServerUiState, viewModel: CreateServerViewModel) {
    val deviceRam = viewModel.deviceRamMb
    val safeLimit = (deviceRam * 0.6f).roundToInt()

    StepScaffold(
        title = "RAM allocation",
        subtitle = "More RAM = happier server, but leave some for your phone's OS.",
        onNext = viewModel::nextStep,
        nextEnabled = true
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // Current value display
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Allocated",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${state.ramMb} MB",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Device total",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$deviceRam MB",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            // Slider — discrete 512 MB steps, range 512 MB up to min(deviceRam, 8 GB).
            // Compose `steps` = number of *intermediate* positions (not counting endpoints),
            // so total positions = steps + 2.  We want positions at 512, 1024, ..., maxRam:
            // total positions = (maxRam - 512) / 512 + 1  →  intermediate = total - 2.
            val maxRam = minOf(deviceRam, 8192)
            val totalPositions = ((maxRam - 512) / 512) + 1
            val steps = (totalPositions - 2).coerceAtLeast(0)
            Slider(
                value = state.ramMb.toFloat(),
                onValueChange = {
                    // Snap to nearest 512 MB boundary
                    val snapped = ((it / 512f).roundToInt() * 512).coerceIn(512, maxRam)
                    viewModel.updateRam(snapped)
                },
                valueRange = 512f..maxRam.toFloat(),
                steps = steps,
                modifier = Modifier.fillMaxWidth()
            )

            // Warning if over safe limit
            if (state.ramMb > safeLimit) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null,
                            tint = Amber400, modifier = Modifier.size(18.dp))
                        Text(
                            text = "Allocating more than 60% of device RAM (${safeLimit} MB) can cause " +
                                   "the OS to kill your server when other apps need memory.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            // Quick-select buttons
            Text("Quick select", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1024, 2048, 4096).forEach { preset ->
                    if (preset <= maxRam) {
                        FilterChip(
                            selected = state.ramMb == preset,
                            onClick = { viewModel.updateRam(preset) },
                            label = { Text("${preset / 1024}GB") }
                        )
                    }
                }
            }
        }
    }
}

// ── Step 5: EULA + Confirm ────────────────────────────────────────────────────

@Composable
private fun EulaStep(state: CreateServerUiState, viewModel: CreateServerViewModel) {
    StepScaffold(
        title = "Review & create",
        subtitle = "Almost there! Review your choices and accept Mojang's EULA to proceed.",
        onNext = viewModel::nextStep,
        nextEnabled = state.eulaAccepted && !state.isCreating,
        nextLabel = if (state.isCreating) "Creating…" else "Create Server"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Summary card
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SummaryRow("Name", state.name)
                    SummaryRow("Type", state.loaderType.name.lowercase().replaceFirstChar { it.uppercase() })
                    SummaryRow("Version", state.selectedVersion)
                    SummaryRow("RAM", "${state.ramMb} MB")
                }
            }

            HorizontalDivider()

            // EULA acceptance
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.updateEula(!state.eulaAccepted) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Checkbox(
                    checked = state.eulaAccepted,
                    onCheckedChange = { viewModel.updateEula(it) }
                )
                Text(
                    buildAnnotatedString {
                        append("I have read and agree to the ")
                        withStyle(SpanStyle(
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline
                        )) {
                            append("Minecraft End User License Agreement (EULA)")
                        }
                        append(". PocketCraft will write this acceptance to eula.txt so the server can start.")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (state.isCreating) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface)
    }
}

// ── Shared scaffold for all steps ─────────────────────────────────────────────

@Composable
private fun StepScaffold(
    title: String,
    subtitle: String,
    onNext: () -> Unit,
    nextEnabled: Boolean,
    nextLabel: String = "Next",
    content: @Composable ColumnScope.() -> Unit
) {
    // Outer column: non-scrollable, fills the screen, button stays pinned at bottom
    Column(modifier = Modifier.fillMaxSize()) {
        // Scrollable content area — uses weight(1f) here, which is valid in a non-scrollable Column
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 24.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }

        // Fixed action button — always visible at bottom, never scrolls away
        Button(
            onClick = onNext,
            enabled = nextEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .height(52.dp)
        ) {
            Text(nextLabel, style = MaterialTheme.typography.labelLarge)
        }
    }
}

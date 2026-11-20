package io.github.s1ddhants1.unhinge.ui.component.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.s1ddhants1.unhinge.Consts
import io.github.s1ddhants1.unhinge.R
import io.github.s1ddhants1.unhinge.ai.AiHttpException
import io.github.s1ddhants1.unhinge.ai.aiFailure
import io.github.s1ddhants1.unhinge.ai.AiModelInfo
import io.github.s1ddhants1.unhinge.ai.AiProviderGroup
import io.github.s1ddhants1.unhinge.ai.AiProviderPreset
import io.github.s1ddhants1.unhinge.ai.AiProviderPresets
import io.github.s1ddhants1.unhinge.ai.AiWingmanClient
import io.github.s1ddhants1.unhinge.ai.OpenRouterAuth
import io.github.s1ddhants1.unhinge.ai.PromptRepository
import io.github.s1ddhants1.unhinge.ui.component.DefaultDialog
import io.github.s1ddhants1.unhinge.ui.component.EnumDialog
import io.github.s1ddhants1.unhinge.ui.component.TextFieldDialog
import io.github.s1ddhants1.unhinge.util.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@Composable
fun AiWingmanSettingsPage(
    prefs: PreferencesManager
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var aiProvider by rememberSaveable { mutableStateOf(AiProviderPresets.normalizeProvider(prefs.aiProvider)) }
    var currentPreset by remember(aiProvider) { mutableStateOf(AiProviderPresets.find(aiProvider) ?: AiProviderPresets.all.first()) }

    var openRouterModel by rememberSaveable { mutableStateOf(prefs.openRouterModel.ifBlank { currentPreset.defaultModel }) }
    var openRouterBaseUrl by rememberSaveable { mutableStateOf(prefs.openRouterBaseUrl.ifBlank { currentPreset.endpoint }) }
    var apiKey by remember(aiProvider) { mutableStateOf(prefs.aiApiKeyFor(currentPreset.id)) }

    var openRouterOAuthKey by remember { mutableStateOf(prefs.openRouterOAuthKey) }
    var openRouterOAuthSession by remember { mutableStateOf(prefs.openRouterOAuthSession) }

    var aiSystemPrompt by rememberSaveable { mutableStateOf(prefs.aiCustomSystemPrompt) }
    var promptRemoteUrl by rememberSaveable { mutableStateOf(prefs.aiPromptRemoteUrl) }
    var aiOverrideSystemPrompt by rememberSaveable { mutableStateOf(prefs.aiOverrideSystemPrompt) }
    var showHostAppFab by rememberSaveable { mutableStateOf(prefs.showHostAppFab) }

    var aiTemperature by rememberSaveable { mutableStateOf(prefs.aiTemperature) }
    var aiTopP by rememberSaveable { mutableStateOf(prefs.aiTopP) }
    var aiReasoningEffort by rememberSaveable { mutableStateOf(prefs.aiReasoningEffort) }

    // Dialog state
    var showProviderPicker by remember { mutableStateOf(false) }
    var showModelPicker by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showBaseUrlDialog by remember { mutableStateOf(false) }
    var showSystemPromptDialog by remember { mutableStateOf(false) }
    var showRemoteUrlDialog by remember { mutableStateOf(false) }
    var showReasoningDialog by remember { mutableStateOf(false) }

    // OpenRouter OAuth PKCE
    val openRouterAuth = remember { OpenRouterAuth() }
    var oauthAwaitingCode by remember {
        mutableStateOf(openRouterOAuthSession.takeIf(String::isNotBlank)?.let(openRouterAuth::restore) == true)
    }
    var oauthInputCode by remember { mutableStateOf("") }
    var oauthBusy by remember { mutableStateOf(false) }

    val oauthConnected = currentPreset.id == "OpenRouter" && openRouterOAuthKey.isNotBlank()

    // Remote prompt sync
    var isSyncingPrompt by remember { mutableStateOf(false) }
    var promptSyncGeneration by remember { mutableStateOf(0) }
    val defaultSystemPrompt = remember(promptSyncGeneration) {
        PromptRepository.getEffectiveSystemPromptTemplate(prefs, context)
    }

    // Connection probe state
    var isTestingConnection by remember { mutableStateOf(false) }
    var testResultLatency by remember { mutableStateOf<Long?>(null) }
    var testResultSample by remember { mutableStateOf<String?>(null) }
    var testErrorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. PROVIDER SECTION ---
        SettingsSectionCard(title = stringResource(R.string.ai_provider)) {
            SettingsActionRow(
                title = currentPreset.id,
                description = when (currentPreset.group) {
                    AiProviderGroup.GATEWAY -> stringResource(R.string.ai_provider_group_gateways)
                    AiProviderGroup.DIRECT -> stringResource(R.string.ai_provider_group_direct)
                    AiProviderGroup.CUSTOM -> stringResource(R.string.ai_provider_group_custom)
                },
                onClick = { showProviderPicker = true }
            )
        }

        // --- 2. CONNECTION SECTION ---
        SettingsSectionCard(title = stringResource(R.string.ai_setup_guide)) {
            // OpenRouter PKCE OAuth
            if (currentPreset.supportsOAuth) {
                if (oauthConnected) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.openrouter_sign_in_title),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = stringResource(R.string.openrouter_signed_in_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        TextButton(
                            onClick = {
                                openRouterOAuthKey = ""
                                prefs.openRouterOAuthKey = ""
                                openRouterOAuthSession = ""
                                prefs.openRouterOAuthSession = ""
                                prefs.saveToFallbackStorageAsync(context)
                                Toast.makeText(context, "Disconnected OpenRouter OAuth", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text(stringResource(R.string.openrouter_disconnect), color = MaterialTheme.colorScheme.error)
                        }
                    }
                } else if (oauthAwaitingCode) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.openrouter_auth_instruction),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        OutlinedTextField(
                            value = oauthInputCode,
                            onValueChange = { oauthInputCode = it },
                            label = { Text(stringResource(R.string.openrouter_auth_code)) },
                            singleLine = true,
                            enabled = !oauthBusy,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    openRouterAuth.cancel()
                                    oauthAwaitingCode = false
                                    openRouterOAuthSession = ""
                                    prefs.openRouterOAuthSession = ""
                                    prefs.saveToFallbackStorageAsync(context)
                                },
                                enabled = !oauthBusy
                            ) {
                                Text(stringResource(R.string.action_cancel))
                            }
                            Spacer(Modifier.width(8.dp))
                            TextButton(
                                onClick = {
                                    oauthBusy = true
                                    coroutineScope.launch {
                                        try {
                                            val key = openRouterAuth.exchange(oauthInputCode)
                                            openRouterOAuthKey = key
                                            prefs.openRouterOAuthKey = key
                                            openRouterOAuthSession = ""
                                            prefs.openRouterOAuthSession = ""
                                            oauthAwaitingCode = false
                                            prefs.saveToFallbackStorageAsync(context)
                                            Toast.makeText(context, "Signed in with OpenRouter!", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Sign-in failed: ${e.message}", Toast.LENGTH_LONG).show()
                                        } finally {
                                            oauthBusy = false
                                        }
                                    }
                                },
                                enabled = !oauthBusy && oauthInputCode.isNotBlank()
                            ) {
                                if (oauthBusy) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Text(stringResource(R.string.action_apply))
                                }
                            }
                        }
                    }
                } else {
                    SettingsActionRow(
                        title = stringResource(R.string.openrouter_sign_in_title),
                        description = stringResource(R.string.openrouter_sign_in_description),
                        icon = Icons.Outlined.Lock,
                        onClick = {
                            try {
                                val url = openRouterAuth.start()
                                openRouterOAuthSession = openRouterAuth.pendingSession().orEmpty()
                                prefs.openRouterOAuthSession = openRouterOAuthSession
                                prefs.saveToFallbackStorageAsync(context)
                                oauthAwaitingCode = true
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not open browser: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
                SettingsDivider()
            }

            // API Key
            SettingsActionRow(
                title = stringResource(R.string.ai_api_key),
                description = when {
                    oauthConnected -> stringResource(R.string.openrouter_signed_in_description)
                    apiKey.isNotBlank() -> "••••••••••••"
                    else -> stringResource(R.string.ai_not_set)
                },
                icon = Icons.Outlined.Key,
                enabled = !oauthConnected,
                onClick = { showApiKeyDialog = true }
            )

            SettingsDivider()

            // Base URL
            SettingsActionRow(
                title = stringResource(R.string.ai_base_url),
                description = openRouterBaseUrl.ifBlank { currentPreset.endpoint },
                icon = Icons.Outlined.Link,
                onClick = { showBaseUrlDialog = true }
            )
        }

        // --- 3. MODEL SECTION ---
        SettingsSectionCard(title = stringResource(R.string.ai_model)) {
            val (credKey, credUrl) = prefs.aiCredentials(currentPreset.id)
            val modelsReady = oauthConnected || credKey.isNotBlank() || currentPreset.publicModelList

            SettingsActionRow(
                title = openRouterModel.ifBlank { currentPreset.defaultModel },
                description = if (!modelsReady) stringResource(R.string.ai_model_api_key_needed) else "Tap to choose or search model",
                icon = Icons.Outlined.Tune,
                enabled = modelsReady,
                onClick = { showModelPicker = true }
            )
        }

        // --- 4. CONNECTION TEST ---
        SettingsSectionCard(title = stringResource(R.string.ai_test_connection)) {
            val (credKey, credUrl) = prefs.aiCredentials(currentPreset.id)
            val canRun = oauthConnected || credKey.isNotBlank() || !AiProviderPresets.isApiKeyRequired(currentPreset.id, openRouterModel)

            Text(
                text = stringResource(R.string.ai_test_connection_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    onClick = {
                        if (!canRun) {
                            testErrorMessage = context.getString(R.string.ai_test_missing_credentials)
                            testResultLatency = null
                            testResultSample = null
                            return@TextButton
                        }
                        isTestingConnection = true
                        testErrorMessage = null
                        testResultLatency = null
                        testResultSample = null

                        coroutineScope.launch {
                            val probe = AiWingmanClient.probeConnection(
                                provider = currentPreset.id,
                                apiKey = credKey,
                                baseUrl = credUrl,
                                model = openRouterModel.ifBlank { currentPreset.defaultModel },
                            )
                            isTestingConnection = false
                            probe.fold(
                                onSuccess = { (sample, latency) ->
                                    testResultLatency = latency
                                    testResultSample = sample
                                    testErrorMessage = null
                                },
                                onFailure = { error ->
                                    testErrorMessage = error.aiFailure().userFriendlyMessage(context)
                                    testResultLatency = null
                                    testResultSample = null
                                }
                            )
                        }
                    },
                    enabled = !isTestingConnection
                ) {
                    if (isTestingConnection) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.ai_test_connection_running))
                    } else {
                        Icon(Icons.Outlined.PlayArrow, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.ai_test_connection))
                    }
                }
            }

            testResultLatency?.let { latency ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.ai_test_connection_success, latency.toString()),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                testResultSample?.let { sample ->
                    Text(
                        text = "Reply preview: $sample",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            testErrorMessage?.let { err ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.ai_test_connection_failed, err),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // --- 5. MODEL PARAMETERS ---
        SettingsSectionCard(title = stringResource(R.string.ai_section_model_params)) {
            SettingsSliderRow(
                title = stringResource(R.string.ai_temperature_title),
                value = aiTemperature,
                onValueChange = {
                    aiTemperature = (it * 20f).roundToInt() / 20f
                    prefs.aiTemperature = aiTemperature
                },
                valueRange = 0.0f..1.5f,
                valueText = String.format(Locale.US, "%.2f", aiTemperature),
                onValueChangeFinished = { prefs.saveToFallbackStorageAsync(context) }
            )

            SettingsDivider()

            SettingsSliderRow(
                title = stringResource(R.string.ai_top_p_title),
                value = aiTopP,
                onValueChange = {
                    aiTopP = (it * 20f).roundToInt() / 20f
                    prefs.aiTopP = aiTopP
                },
                valueRange = 0.0f..1.0f,
                valueText = String.format(Locale.US, "%.2f", aiTopP),
                onValueChangeFinished = { prefs.saveToFallbackStorageAsync(context) }
            )

            SettingsDivider()

            SettingsActionRow(
                title = stringResource(R.string.ai_reasoning_effort_title),
                description = aiReasoningEffort.replaceFirstChar { it.uppercase() },
                onClick = { showReasoningDialog = true }
            )
        }

        // --- 6. WINGMAN PROMPTS & INTEGRATION ---
        SettingsSectionCard(title = stringResource(R.string.ai_section_prompt)) {
            SettingsActionRow(
                title = stringResource(R.string.ai_system_prompt_title),
                description = if (aiSystemPrompt.isBlank()) stringResource(R.string.ai_system_prompt_default) else "${aiSystemPrompt.length} chars",
                icon = Icons.Outlined.Edit,
                onClick = { showSystemPromptDialog = true }
            )

            SettingsDivider()

            SettingsActionRow(
                title = stringResource(R.string.ai_remote_sync_title),
                description = stringResource(R.string.ai_remote_sync_desc),
                icon = Icons.Outlined.CloudDownload,
                trailingContent = {
                    if (isSyncingPrompt) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(
                            text = stringResource(R.string.refresh),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                onClick = {
                    if (isSyncingPrompt) return@SettingsActionRow
                    isSyncingPrompt = true
                    coroutineScope.launch {
                        val result = PromptRepository.fetchRemotePrompt(promptRemoteUrl)
                        isSyncingPrompt = false
                        result.fold(
                            onSuccess = { fetched ->
                                prefs.aiRemoteSystemPrompt = fetched
                                prefs.saveToFallbackStorageAsync(context)
                                promptSyncGeneration++
                                Toast.makeText(context, R.string.ai_remote_sync_success, Toast.LENGTH_SHORT).show()
                            },
                            onFailure = { error ->
                                Toast.makeText(context, context.getString(R.string.ai_remote_sync_failure, error.message), Toast.LENGTH_LONG).show()
                            }
                        )
                    }
                }
            )

            SettingsDivider()

            SettingsActionRow(
                title = stringResource(R.string.ai_remote_url_title),
                description = promptRemoteUrl,
                icon = Icons.Outlined.Link,
                onClick = { showRemoteUrlDialog = true }
            )

            SettingsDivider()

            SettingsSwitchRow(
                title = "Override default prompt",
                description = "Always use custom system prompt instructions",
                checked = aiOverrideSystemPrompt,
                onCheckedChange = {
                    aiOverrideSystemPrompt = it
                    prefs.aiOverrideSystemPrompt = it
                    prefs.saveToFallbackStorageAsync(context)
                }
            )

            SettingsDivider()

            SettingsSwitchRow(
                title = stringResource(R.string.ai_fab_title),
                description = "Shows the floating wingman button over profiles and chats in Hinge",
                icon = Icons.Outlined.TouchApp,
                checked = showHostAppFab,
                onCheckedChange = {
                    showHostAppFab = it
                    prefs.showHostAppFab = it
                    prefs.saveToFallbackStorageAsync(context)
                }
            )
        }
    }

    // --- DIALOGS ---

    // 1. Provider Picker Dialog
    if (showProviderPicker) {
        DefaultDialog(
            onDismiss = { showProviderPicker = false },
            title = { Text(stringResource(R.string.ai_provider)) },
            buttons = {
                TextButton(onClick = { showProviderPicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        ) {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                AiProviderGroup.entries.forEach { group ->
                    val groupPresets = AiProviderPresets.all.filter { it.group == group }
                    if (groupPresets.isNotEmpty()) {
                        item(key = "header_${group.name}") {
                            Text(
                                text = stringResource(
                                    when (group) {
                                        AiProviderGroup.GATEWAY -> R.string.ai_provider_group_gateways
                                        AiProviderGroup.DIRECT -> R.string.ai_provider_group_direct
                                        AiProviderGroup.CUSTOM -> R.string.ai_provider_group_custom
                                    }
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        items(groupPresets, key = { it.id }) { preset ->
                            val isSelected = preset.id == currentPreset.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.small)
                                    .selectable(
                                        selected = isSelected,
                                        role = Role.RadioButton,
                                        onClick = {
                                            aiProvider = preset.id
                                            currentPreset = preset
                                            prefs.aiProvider = preset.id
                                            if (preset.defaultModel.isNotBlank()) {
                                                openRouterModel = preset.defaultModel
                                                prefs.openRouterModel = preset.defaultModel
                                            }
                                            if (preset.endpoint.isNotBlank()) {
                                                openRouterBaseUrl = preset.endpoint
                                                prefs.openRouterBaseUrl = preset.endpoint
                                            }
                                            apiKey = prefs.aiApiKeyFor(preset.id)
                                            if (preset.id != "OpenRouter") {
                                                openRouterOAuthSession = ""
                                                prefs.openRouterOAuthSession = ""
                                            }
                                            prefs.saveToFallbackStorageAsync(context)
                                            showProviderPicker = false
                                        }
                                    )
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = preset.id,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                    if (preset.defaultModel.isNotBlank()) {
                                        Text(
                                            text = preset.defaultModel,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 2. Model Picker Dialog
    if (showModelPicker) {
        val (modelApiKey, modelBaseUrl) = prefs.aiCredentials(currentPreset.id)
        var reloadTrigger by remember { mutableIntStateOf(0) }
        var searchQuery by remember { mutableStateOf("") }

        val modelState by produceState<Result<List<AiModelInfo>>?>(null, currentPreset.id, modelApiKey, modelBaseUrl, reloadTrigger) {
            value = null
            value = AiWingmanClient.modelCatalog.models(
                preset = currentPreset,
                apiKey = modelApiKey,
                baseUrl = modelBaseUrl,
                refresh = reloadTrigger > 0
            )
        }

        DefaultDialog(
            onDismiss = { showModelPicker = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.ai_model))
                    IconButton(onClick = { reloadTrigger++ }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh")
                    }
                }
            },
            buttons = {
                TextButton(onClick = { showModelPicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text(stringResource(R.string.ai_model_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )

                val typed = searchQuery.trim()
                val catalogModels = modelState?.getOrNull().orEmpty()

                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                    // Custom typed model option if query not found
                    if (typed.isNotEmpty() && catalogModels.none { it.id.equals(typed, ignoreCase = true) }) {
                        item(key = "custom_typed") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable {
                                        openRouterModel = typed
                                        prefs.openRouterModel = typed
                                        prefs.saveToFallbackStorageAsync(context)
                                        showModelPicker = false
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${stringResource(R.string.ai_model_custom_id)}: $typed",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    when {
                        modelState == null -> {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(strokeWidth = 2.dp)
                                }
                            }
                        }
                        modelState?.isFailure == true -> {
                            item {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = stringResource(R.string.ai_model_load_failed),
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    TextButton(onClick = { reloadTrigger++ }) {
                                        Text(stringResource(R.string.retry))
                                    }
                                }
                            }
                        }
                        else -> {
                            val filtered = catalogModels.filter {
                                typed.isEmpty() || it.id.contains(typed, ignoreCase = true) || it.name.contains(typed, ignoreCase = true)
                            }
                            if (filtered.isEmpty() && typed.isNotEmpty()) {
                                item {
                                    Text(
                                        text = stringResource(R.string.ai_model_none_found),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            }
                            items(filtered, key = { it.id }) { modelInfo ->
                                val isSelected = modelInfo.id == openRouterModel
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(MaterialTheme.shapes.small)
                                        .selectable(
                                            selected = isSelected,
                                            role = Role.RadioButton,
                                            onClick = {
                                                openRouterModel = modelInfo.id
                                                prefs.openRouterModel = modelInfo.id
                                                prefs.saveToFallbackStorageAsync(context)
                                                showModelPicker = false
                                            }
                                        )
                                        .padding(vertical = 10.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = modelInfo.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                        if (modelInfo.name != modelInfo.id) {
                                            Text(
                                                text = modelInfo.id,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        // Pricing badge
                                        val priceText = when {
                                            modelInfo.free -> stringResource(R.string.ai_model_free)
                                            modelInfo.inputUsdPerMTok != null && modelInfo.outputUsdPerMTok != null -> {
                                                stringResource(
                                                    R.string.ai_model_price,
                                                    formatUsd(modelInfo.inputUsdPerMTok),
                                                    formatUsd(modelInfo.outputUsdPerMTok)
                                                )
                                            }
                                            else -> null
                                        }
                                        priceText?.let {
                                            Text(
                                                text = it,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (modelInfo.free) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                    }
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
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

    // 3. API Key Dialog
    if (showApiKeyDialog) {
        TextFieldDialog(
            title = { Text(stringResource(R.string.ai_api_key)) },
            initialTextFieldValue = TextFieldValue(apiKey),
            singleLine = true,
            keyboardType = KeyboardType.Password,
            placeholder = { Text("Enter API key") },
            onDone = { newKey ->
                apiKey = newKey.trim()
                prefs.setAiApiKey(currentPreset.id, apiKey)
                prefs.saveToFallbackStorageAsync(context)
                showApiKeyDialog = false
            },
            onDismiss = { showApiKeyDialog = false }
        )
    }

    // 4. Base URL Dialog
    if (showBaseUrlDialog) {
        TextFieldDialog(
            title = { Text(stringResource(R.string.ai_base_url)) },
            initialTextFieldValue = TextFieldValue(openRouterBaseUrl.ifBlank { currentPreset.endpoint }),
            singleLine = true,
            keyboardType = KeyboardType.Uri,
            placeholder = { Text(currentPreset.endpoint) },
            extraContent = {
                if (currentPreset.endpoint.isNotBlank()) {
                    TextButton(
                        onClick = {
                            openRouterBaseUrl = currentPreset.endpoint
                            prefs.openRouterBaseUrl = currentPreset.endpoint
                            prefs.saveToFallbackStorageAsync(context)
                            showBaseUrlDialog = false
                        }
                    ) {
                        Text(stringResource(R.string.ai_provider_default))
                    }
                }
            },
            onDone = { newUrl ->
                openRouterBaseUrl = newUrl.trim()
                prefs.openRouterBaseUrl = openRouterBaseUrl
                prefs.saveToFallbackStorageAsync(context)
                showBaseUrlDialog = false
            },
            onDismiss = { showBaseUrlDialog = false }
        )
    }

    // 5. Reasoning Effort Dialog
    if (showReasoningDialog) {
        val options = listOf("low", "medium", "high")
        EnumDialog(
            title = stringResource(R.string.ai_reasoning_effort_title),
            current = aiReasoningEffort,
            values = options,
            valueText = { it.replaceFirstChar { c -> c.uppercase() } },
            onDismiss = { showReasoningDialog = false },
            onSelect = { selected ->
                aiReasoningEffort = selected
                prefs.aiReasoningEffort = selected
                prefs.saveToFallbackStorageAsync(context)
                showReasoningDialog = false
            }
        )
    }

    // 6. System Prompt Dialog
    if (showSystemPromptDialog) {
        TextFieldDialog(
            title = { Text(stringResource(R.string.ai_system_prompt_title)) },
            initialTextFieldValue = TextFieldValue(aiSystemPrompt),
            singleLine = false,
            maxLines = 15,
            placeholder = { Text(defaultSystemPrompt) },
            extraContent = {
                if (aiSystemPrompt.isNotBlank()) {
                    TextButton(
                        onClick = {
                            aiSystemPrompt = ""
                            prefs.aiCustomSystemPrompt = ""
                            prefs.saveToFallbackStorageAsync(context)
                            showSystemPromptDialog = false
                        }
                    ) {
                        Text(stringResource(R.string.reset))
                    }
                }
            },
            onDone = { newPrompt ->
                aiSystemPrompt = newPrompt.trim()
                prefs.aiCustomSystemPrompt = aiSystemPrompt
                prefs.saveToFallbackStorageAsync(context)
                showSystemPromptDialog = false
            },
            onDismiss = { showSystemPromptDialog = false }
        )
    }

    // 7. Remote Prompt Source URL Dialog
    if (showRemoteUrlDialog) {
        TextFieldDialog(
            title = { Text(stringResource(R.string.ai_remote_url_title)) },
            initialTextFieldValue = TextFieldValue(promptRemoteUrl),
            singleLine = true,
            keyboardType = KeyboardType.Uri,
            placeholder = { Text(Consts.DEFAULT_AI_PROMPT_REMOTE_URL) },
            extraContent = {
                TextButton(
                    onClick = {
                        promptRemoteUrl = Consts.DEFAULT_AI_PROMPT_REMOTE_URL
                        prefs.aiPromptRemoteUrl = promptRemoteUrl
                        prefs.saveToFallbackStorageAsync(context)
                        showRemoteUrlDialog = false
                    }
                ) {
                    Text(stringResource(R.string.reset))
                }
            },
            onDone = { newUrl ->
                promptRemoteUrl = newUrl.trim()
                prefs.aiPromptRemoteUrl = promptRemoteUrl
                prefs.saveToFallbackStorageAsync(context)
                showRemoteUrlDialog = false
            },
            onDismiss = { showRemoteUrlDialog = false }
        )
    }
}

private fun formatUsd(value: Double): String {
    val digits = if (value >= 1.0) 2 else 3
    val scale = 10.0.pow(digits).roundToLong()
    val scaled = (value * scale).roundToLong()
    return "\$${scaled / scale}.${(scaled % scale).toString().padStart(digits, '0')}"
}

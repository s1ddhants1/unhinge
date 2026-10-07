package io.github.s1ddhants1.unhinge.ui.component.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.s1ddhants1.unhinge.Consts
import io.github.s1ddhants1.unhinge.R
import io.github.s1ddhants1.unhinge.ai.AiWingmanHelper
import io.github.s1ddhants1.unhinge.ai.ModelReasoningCatalog
import io.github.s1ddhants1.unhinge.ai.PromptRepository
import io.github.s1ddhants1.unhinge.ai.ZenRouter
import kotlinx.coroutines.launch
import io.github.s1ddhants1.unhinge.ui.component.EnumDialog
import io.github.s1ddhants1.unhinge.ui.component.settings.SettingsActionRow
import io.github.s1ddhants1.unhinge.ui.component.settings.SettingsDivider
import io.github.s1ddhants1.unhinge.ui.component.settings.SettingsSectionCard
import io.github.s1ddhants1.unhinge.ui.component.settings.SettingsSliderRow
import io.github.s1ddhants1.unhinge.ui.component.settings.SettingsSwitchRow
import io.github.s1ddhants1.unhinge.ui.component.TextFieldDialog
import io.github.s1ddhants1.unhinge.util.PreferencesManager
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun AiWingmanSettingsPage(
    prefs: PreferencesManager
) {
    val context = LocalContext.current
    var aiProvider by rememberSaveable { mutableStateOf(prefs.aiProvider) }
    var openRouterApiKey by rememberSaveable { mutableStateOf(prefs.openRouterApiKey) }
    var openRouterBaseUrl by rememberSaveable { mutableStateOf(prefs.openRouterBaseUrl) }
    var openRouterModel by rememberSaveable { mutableStateOf(prefs.openRouterModel) }

    var aiSystemPrompt by rememberSaveable { mutableStateOf(prefs.aiCustomSystemPrompt) }
    var openerPromptTemplate by rememberSaveable { mutableStateOf(prefs.aiOpenerPromptTemplate) }
    var promptRemoteUrl by rememberSaveable { mutableStateOf(prefs.aiPromptRemoteUrl) }
    var isSyncingRemote by remember { mutableStateOf(false) }
    var showOpenerTemplateDialog by rememberSaveable { mutableStateOf(false) }
    var showRemoteUrlDialog by rememberSaveable { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    var aiTemperature by rememberSaveable { mutableStateOf(prefs.aiTemperature) }
    var aiTopP by rememberSaveable { mutableStateOf(prefs.aiTopP) }
    var aiReasoningEffort by rememberSaveable { mutableStateOf(prefs.aiReasoningEffort) }

    val aiProviders =
        mapOf(
            "Zen" to Consts.ZEN_DEFAULT_BASE_URL,
            "OpenRouter" to "https://openrouter.ai/api/v1/chat/completions",
            "OpenAI" to "https://api.openai.com/v1/chat/completions",
            "Perplexity" to "https://api.perplexity.ai/chat/completions",
            "Claude" to "https://api.anthropic.com/v1/messages",
            "Gemini" to "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
            "XAi" to "https://api.x.ai/v1/chat/completions",
            "Mistral" to "https://api.mistral.ai/v1/chat/completions",
            "Inception" to "https://api.inceptionlabs.ai/v1/chat/completions",
            "Custom" to "",
        )

    val providerHelpText =
        mapOf(
            "Zen" to stringResource(R.string.ai_provider_zen_help),
            "OpenRouter" to stringResource(R.string.ai_provider_openrouter_help),
            "OpenAI" to stringResource(R.string.ai_provider_openai_help),
            "Perplexity" to stringResource(R.string.ai_provider_perplexity_help),
            "Claude" to stringResource(R.string.ai_provider_claude_help),
            "Gemini" to stringResource(R.string.ai_provider_gemini_help),
            "XAi" to stringResource(R.string.ai_provider_xai_help),
            "Mistral" to stringResource(R.string.ai_provider_mistral_help),
            "Inception" to stringResource(R.string.ai_provider_inception_help),
            "Custom" to "",
        )

    val modelsByProvider =
        mapOf(
            "Zen" to
                listOf(
                    "space-bunny-free",
                    "muse-spark-1.3-contributor-free",
                    "big-pickle",
                    "fledge-alpha-free",
                    "ling-3.1-flash-free",
                    "longcat-2.5-preview-free",
                    "mimo-v2.6-flash-free",
                    "mimo-v2.5-free",
                    "nemotron-3-ultra-free",
                    "nemotron-3.5-lightning-free",
                    "claude-sonnet-5",
                    "claude-haiku-4-5",
                    "gpt-5.6-sol",
                    "gemini-3.8-flash",
                    "deepseek-v4-flash",
                    "deepseek-v4-pro",
                    "glm-5.3-flash",
                    "qwen3.8-flash",
                    "kimi-k2.5",
                    "minimax-m3",
                ),
            "OpenRouter" to
                listOf(
                    "inception/mercury-2.5-preview",
                    "meta/muse-spark-1.3",
                    "z-ai/glm-5.3-flash",
                    "qwen/qwen3.8-flash",
                    "deepseek/deepseek-v4-flash-latest",
                    "openai/gpt-mini-latest",
                    "openai/gpt-oss-120b",
                    "google/gemini-flash-latest",
                ),
            "OpenAI" to
                listOf(
                    "gpt-5.6-sol",
                    "gpt-5.6-terra",
                    "gpt-5.6-luna",
                    "gpt-5.5-2026-04-23",
                    "gpt-5.4-2026-03-05",
                ),
            "Claude" to
                listOf(
                    "claude-opus-5",
                    "claude-sonnet-5",
                    "claude-haiku-4-5-20251001",
                    "claude-fable-5-1",
                ),
            "Gemini" to
                listOf(
                    "gemini-flash-lite-latest",
                    "gemini-pro-latest",
                    "gemini-flash-latest",
                    "gemini-3.8-flash",
                ),
            "Perplexity" to
                listOf(
                    "sonar",
                    "sonar-pro",
                    "sonar-reasoning-pro",
                ),
            "XAi" to
                listOf(
                    "grok-4.3",
                    "grok-4.6",
                    "grok-4.1-fast",
                ),
            "Mistral" to
                listOf(
                    "mistral-large-latest",
                    "mistral-medium-latest",
                    "mistral-small-latest",
                    "mistral-tiny-latest",
                ),
            "Inception" to
                listOf(
                    "mercury-2",
                ),
            "Custom" to listOf(),
        )

    val commonModels = modelsByProvider[aiProvider] ?: listOf()

    var showProviderDialog by rememberSaveable { mutableStateOf(false) }
    var showProviderHelpDialog by rememberSaveable { mutableStateOf(false) }
    var showApiKeyDialog by rememberSaveable { mutableStateOf(false) }
    var showBaseUrlDialog by rememberSaveable { mutableStateOf(false) }
    var showModelDialog by rememberSaveable { mutableStateOf(false) }
    var showCustomModelInput by rememberSaveable { mutableStateOf(false) }
    var showReasoningEffortDialog by rememberSaveable { mutableStateOf(false) }

    var showSystemPromptDialog by rememberSaveable { mutableStateOf(false) }

    if (showProviderHelpDialog) {
        AlertDialog(
            onDismissRequest = { showProviderHelpDialog = false },
            confirmButton = {
                TextButton(onClick = { showProviderHelpDialog = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            icon = { Icon(Icons.Outlined.Info, null) },
            title = { Text(stringResource(R.string.ai_provider_help)) },
            text = {
                Column {
                    providerHelpText.forEach { (provider, help) ->
                        if (help.isNotEmpty()) {
                            val primaryColor = MaterialTheme.colorScheme.primary
                            val annotatedString =
                                buildAnnotatedString {
                                    append("$provider: ")
                                    val urlRegex = "https?://[^\\s]+".toRegex()
                                    val match = urlRegex.find(help)
                                    if (match != null) {
                                        val url = match.value
                                        val beforeUrl = help.substring(0, match.range.first)
                                        val afterUrl = help.substring(match.range.last + 1)

                                        append(beforeUrl)
                                        val linkStart = length
                                        append(url)
                                        val linkEnd = length
                                        append(afterUrl)

                                        addLink(
                                            LinkAnnotation.Url(
                                                url = url,
                                                styles =
                                                    TextLinkStyles(
                                                        style =
                                                            SpanStyle(
                                                                color = primaryColor,
                                                                textDecoration = TextDecoration.Underline,
                                                            ),
                                                    ),
                                            ),
                                            start = linkStart,
                                            end = linkEnd,
                                        )
                                    } else {
                                        append(help)
                                    }
                                }

                            Text(
                                text = annotatedString,
                                style =
                                    MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurface,
                                    ),
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                    }
                }
            },
        )
    }

    if (showProviderDialog) {
        EnumDialog(
            onDismiss = { showProviderDialog = false },
            onSelect = {
                aiProvider = it
                prefs.aiProvider = it
                if (it != "Custom") {
                    val url = aiProviders[it] ?: ""
                    openRouterBaseUrl = url
                    prefs.openRouterBaseUrl = url
                } else {
                    openRouterBaseUrl = ""
                    prefs.openRouterBaseUrl = ""
                }
                val modelsForProvider = modelsByProvider[it] ?: listOf()
                openRouterModel =
                    if (modelsForProvider.isNotEmpty()) {
                        modelsForProvider[0]
                    } else {
                        ""
                    }
                prefs.openRouterModel = openRouterModel
                prefs.saveToFallbackStorageAsync(context)
                showProviderDialog = false
            },
            title = stringResource(R.string.ai_provider),
            current = aiProvider,
            values = aiProviders.keys.toList(),
            valueText = { it },
        )
    }

    if (showApiKeyDialog) {
        TextFieldDialog(
            title = { Text(stringResource(R.string.ai_api_key)) },
            icon = { Icon(Icons.Outlined.Key, null) },
            initialTextFieldValue = TextFieldValue(text = openRouterApiKey),
            placeholder = {
                if (aiProvider == "Zen") {
                    Text(stringResource(R.string.ai_api_key_placeholder))
                } else {
                    Text(stringResource(R.string.ai_not_set))
                }
            },
            onDone = {
                val trimmed = it.trim()
                openRouterApiKey = trimmed
                prefs.openRouterApiKey = trimmed
                prefs.saveToFallbackStorageAsync(context)
                showApiKeyDialog = false
            },
            onDismiss = { showApiKeyDialog = false },
        )
    }

    if (showBaseUrlDialog && aiProvider == "Custom") {
        TextFieldDialog(
            title = { Text(stringResource(R.string.ai_base_url)) },
            icon = { Icon(Icons.Outlined.Link, null) },
            initialTextFieldValue = TextFieldValue(text = openRouterBaseUrl),
            onDone = {
                openRouterBaseUrl = it
                prefs.openRouterBaseUrl = it
                prefs.saveToFallbackStorageAsync(context)
                showBaseUrlDialog = false
            },
            onDismiss = { showBaseUrlDialog = false },
        )
    }

    if (showModelDialog) {
        EnumDialog(
            onDismiss = { showModelDialog = false },
            onSelect = {
                if (it == "custom_input") {
                    showCustomModelInput = true
                    showModelDialog = false
                } else {
                    openRouterModel = it
                    prefs.openRouterModel = it
                    prefs.saveToFallbackStorageAsync(context)
                    showModelDialog = false
                }
            },
            title = stringResource(R.string.ai_model),
            current = if (openRouterModel in commonModels) openRouterModel else "custom_input",
            values = commonModels + "custom_input",
            valueText = {
                if (it == "custom_input") {
                    stringResource(R.string.ai_custom_model)
                } else if (aiProvider == "Zen") {
                    if (ZenRouter.isFreeModel(it)) {
                        stringResource(R.string.zen_model_free_format, it)
                    } else {
                        stringResource(R.string.zen_model_paid_format, it)
                    }
                } else {
                    it
                }
            },
        )
    }

    if (showCustomModelInput) {
        TextFieldDialog(
            title = { Text(stringResource(R.string.ai_model)) },
            icon = { Icon(Icons.Outlined.Tune, null) },
            initialTextFieldValue = TextFieldValue(text = openRouterModel),
            onDone = {
                val effective = if (aiProvider == "Zen") ZenRouter.normalizeModelId(it) else it
                openRouterModel = effective
                prefs.openRouterModel = effective
                prefs.saveToFallbackStorageAsync(context)
                showCustomModelInput = false
            },
            onDismiss = { showCustomModelInput = false },
        )
    }

    if (showReasoningEffortDialog) {
        val options = ModelReasoningCatalog.getAvailableOptions(openRouterModel, aiProvider)
        when (val support = ModelReasoningCatalog.getReasoningSupport(openRouterModel, aiProvider)) {
            is ModelReasoningCatalog.ReasoningSupport.Unsupported -> {
                val unsupportedModelLabel = openRouterModel.ifBlank { stringResource(R.string.ai_current_model_fallback) }
                AlertDialog(
                    onDismissRequest = { showReasoningEffortDialog = false },
                    title = { Text(stringResource(R.string.ai_reasoning_unsupported_title)) },
                    text = {
                        Text(
                            stringResource(R.string.ai_reasoning_unsupported_message, unsupportedModelLabel)
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { showReasoningEffortDialog = false }) {
                            Text(stringResource(R.string.action_ok))
                        }
                    },
                )
            }
            is ModelReasoningCatalog.ReasoningSupport.Fixed -> {
                val fixedModelLabel = openRouterModel.ifBlank { stringResource(R.string.ai_current_model_fallback) }
                AlertDialog(
                    onDismissRequest = { showReasoningEffortDialog = false },
                    title = { Text(stringResource(R.string.ai_reasoning_fixed_title)) },
                    text = {
                        Text(
                            stringResource(R.string.ai_reasoning_fixed_message, fixedModelLabel)
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { showReasoningEffortDialog = false }) {
                            Text(stringResource(R.string.action_ok))
                        }
                    },
                )
            }
            else -> {
                val currentSelection = if (options.any { it.first == aiReasoningEffort }) aiReasoningEffort else "default"
                EnumDialog(
                    onDismiss = { showReasoningEffortDialog = false },
                    onSelect = {
                        aiReasoningEffort = it
                        prefs.aiReasoningEffort = it
                        prefs.saveToFallbackStorageAsync(context)
                        showReasoningEffortDialog = false
                    },
                    title = stringResource(R.string.ai_reasoning_level_title, openRouterModel.substringAfterLast('/').ifBlank { stringResource(R.string.ai_reasoning_level_fallback) }),
                    current = currentSelection,
                    values = options.map { it.first },
                    valueText = { key -> options.firstOrNull { it.first == key }?.second ?: ModelReasoningCatalog.levelLabel(key) },
                )
            }
        }
    }

    if (showOpenerTemplateDialog) {
        val defaultTemplate = remember { PromptRepository.getEffectiveOpenerTemplate(context = context) }
        TextFieldDialog(
            title = { Text(stringResource(R.string.ai_opener_prompt_title)) },
            icon = { Icon(Icons.Outlined.Edit, null) },
            initialTextFieldValue = TextFieldValue(text = openerPromptTemplate.ifBlank { defaultTemplate }),
            singleLine = false,
            maxLines = 16,
            isInputValid = { true },
            onDone = {
                openerPromptTemplate = if (it.isBlank() || it == defaultTemplate) "" else it
                prefs.aiOpenerPromptTemplate = openerPromptTemplate
                showOpenerTemplateDialog = false
            },
            onDismiss = { showOpenerTemplateDialog = false },
            extraContent = {
                if (openerPromptTemplate.isNotBlank()) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            onClick = {
                                openerPromptTemplate = ""
                                prefs.aiOpenerPromptTemplate = ""
                                showOpenerTemplateDialog = false
                            },
                        ) {
                            Text(stringResource(R.string.action_reset_to_default))
                        }
                    }
                }
            },
        )
    }

    if (showRemoteUrlDialog) {
        TextFieldDialog(
            title = { Text(stringResource(R.string.ai_remote_url_title)) },
            icon = { Icon(Icons.Outlined.Link, null) },
            initialTextFieldValue = TextFieldValue(text = promptRemoteUrl.ifBlank { Consts.DEFAULT_AI_PROMPT_REMOTE_URL }),
            singleLine = true,
            isInputValid = { it.isNotBlank() },
            onDone = {
                promptRemoteUrl = if (it.isBlank()) Consts.DEFAULT_AI_PROMPT_REMOTE_URL else it.trim()
                prefs.aiPromptRemoteUrl = promptRemoteUrl
                showRemoteUrlDialog = false
            },
            onDismiss = { showRemoteUrlDialog = false },
            extraContent = {
                if (promptRemoteUrl != Consts.DEFAULT_AI_PROMPT_REMOTE_URL) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            onClick = {
                                promptRemoteUrl = Consts.DEFAULT_AI_PROMPT_REMOTE_URL
                                prefs.aiPromptRemoteUrl = Consts.DEFAULT_AI_PROMPT_REMOTE_URL
                                showRemoteUrlDialog = false
                            },
                        ) {
                            Text(stringResource(R.string.action_reset_to_default))
                        }
                    }
                }
            },
        )
    }

    if (showSystemPromptDialog) {
        TextFieldDialog(
            title = { Text(stringResource(R.string.ai_system_prompt_title)) },
            icon = { Icon(Icons.Outlined.Edit, null) },
            initialTextFieldValue = TextFieldValue(text = aiSystemPrompt.ifBlank { Consts.DEFAULT_AI_SYSTEM_PROMPT }),
            singleLine = false,
            maxLines = 12,
            isInputValid = { true },
            onDone = {
                aiSystemPrompt = if (it.isBlank() || it == Consts.DEFAULT_AI_SYSTEM_PROMPT) "" else it
                prefs.aiCustomSystemPrompt = aiSystemPrompt
                prefs.aiOverrideSystemPrompt = aiSystemPrompt.isNotBlank()
                showSystemPromptDialog = false
            },
            onDismiss = { showSystemPromptDialog = false },
            extraContent = {
                if (aiSystemPrompt.isNotBlank()) {
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            onClick = {
                                aiSystemPrompt = ""
                                prefs.aiCustomSystemPrompt = ""
                                prefs.aiOverrideSystemPrompt = false
                                showSystemPromptDialog = false
                            },
                        ) {
                            Text(stringResource(R.string.action_reset_to_default))
                        }
                    }
                }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(4.dp))

        SettingsSectionCard(title = stringResource(R.string.ai_section_activation)) {
            SettingsSwitchRow(
                title = stringResource(R.string.ai_fab_title),
                icon = Icons.Outlined.TouchApp,
                checked = prefs.showHostAppFab,
                onCheckedChange = { prefs.showHostAppFab = it }
            )
        }

        SettingsSectionCard(title = stringResource(R.string.ai_provider)) {
            SettingsActionRow(
                title = stringResource(R.string.ai_provider),
                description = aiProvider,
                icon = Icons.Outlined.Explore,
                onClick = { showProviderDialog = true },
                trailingContent = {
                    IconButton(onClick = { showProviderHelpDialog = true }) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = stringResource(R.string.ai_provider_help),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            )
            if (aiProvider == "Custom") {
                SettingsDivider()
                SettingsActionRow(
                    title = stringResource(R.string.ai_base_url),
                    description = openRouterBaseUrl.ifBlank { stringResource(R.string.ai_not_set) },
                    icon = Icons.Outlined.Link,
                    onClick = { showBaseUrlDialog = true }
                )
            }
        }

        val isApiKeyRequired = AiWingmanHelper.isApiKeyRequired(aiProvider, openRouterModel)

        SettingsSectionCard(title = stringResource(R.string.ai_setup_guide)) {
            SettingsActionRow(
                title = stringResource(R.string.ai_api_key),
                description = if (openRouterApiKey.isNotEmpty()) {
                    "•".repeat(minOf(openRouterApiKey.length, 8))
                } else {
                    stringResource(R.string.ai_not_set)
                },
                icon = Icons.Outlined.Key,
                enabled = isApiKeyRequired,
                onClick = { if (isApiKeyRequired) showApiKeyDialog = true }
            )
            SettingsDivider()
            SettingsActionRow(
                title = stringResource(R.string.ai_model),
                description = openRouterModel.ifBlank { stringResource(R.string.ai_not_set) },
                icon = Icons.Outlined.Tune,
                onClick = { showModelDialog = true }
            )
        }

        SettingsSectionCard(title = stringResource(R.string.ai_section_model_params)) {
            SettingsSliderRow(
                title = stringResource(R.string.ai_temperature_title),
                value = aiTemperature,
                onValueChange = {
                    aiTemperature = ((it / 0.05f).roundToInt() * 0.05f).coerceIn(0f, 1.5f)
                },
                valueRange = 0f..1.5f,
                onValueChangeFinished = {
                    prefs.aiTemperature = aiTemperature
                    prefs.saveToFallbackStorageAsync(context)
                },
                valueText = String.format(Locale.US, "%.2f", aiTemperature),
                icon = Icons.Outlined.Tune
            )
            SettingsDivider()
            SettingsSliderRow(
                title = stringResource(R.string.ai_top_p_title),
                value = aiTopP,
                onValueChange = {
                    aiTopP = ((it / 0.05f).roundToInt() * 0.05f).coerceIn(0.1f, 1f)
                },
                valueRange = 0.1f..1f,
                onValueChangeFinished = {
                    prefs.aiTopP = aiTopP
                    prefs.saveToFallbackStorageAsync(context)
                },
                valueText = String.format(Locale.US, "%.2f", aiTopP),
                icon = Icons.Outlined.Tune
            )
            SettingsDivider()
            SettingsActionRow(
                title = stringResource(R.string.ai_reasoning_effort_title),
                description = ModelReasoningCatalog.getEffortSummary(aiReasoningEffort, openRouterModel, aiProvider),
                icon = Icons.Outlined.Tune,
                onClick = { showReasoningEffortDialog = true }
            )
        }

        SettingsSectionCard(title = stringResource(R.string.ai_section_prompt)) {
            SettingsActionRow(
                title = stringResource(R.string.ai_opener_prompt_title),
                description = if (openerPromptTemplate.isNotBlank()) {
                    stringResource(R.string.ai_opener_prompt_custom)
                } else {
                    stringResource(R.string.ai_opener_prompt_default)
                },
                icon = Icons.Outlined.Edit,
                onClick = { showOpenerTemplateDialog = true }
            )
            SettingsDivider()
            SettingsActionRow(
                title = stringResource(R.string.ai_remote_sync_title),
                description = if (isSyncingRemote) {
                    stringResource(R.string.ai_remote_sync_in_progress)
                } else {
                    stringResource(R.string.ai_remote_sync_desc)
                },
                icon = Icons.Outlined.Refresh,
                onClick = {
                    if (!isSyncingRemote) {
                        isSyncingRemote = true
                        coroutineScope.launch {
                            val syncResult = PromptRepository.fetchRemoteTemplate(promptRemoteUrl)
                            isSyncingRemote = false
                            syncResult.fold(
                                onSuccess = { newTemplate ->
                                    openerPromptTemplate = newTemplate
                                    prefs.aiOpenerPromptTemplate = newTemplate
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.ai_remote_sync_success),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                onFailure = { err ->
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.ai_remote_sync_failure, err.message ?: "Network error"),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            )
                        }
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
            SettingsActionRow(
                title = stringResource(R.string.ai_system_prompt_title),
                description = if (aiSystemPrompt.isNotBlank()) {
                    aiSystemPrompt.take(60).let {
                        if (aiSystemPrompt.length > 60) "$it…" else it
                    }
                } else {
                    stringResource(R.string.ai_system_prompt_default)
                },
                icon = Icons.Outlined.Edit,
                onClick = { showSystemPromptDialog = true }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

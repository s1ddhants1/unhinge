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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Link
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
import io.github.s1ddhants1.unhinge.ui.component.EnumDialog
import io.github.s1ddhants1.unhinge.ui.component.Material3SettingsGroup
import io.github.s1ddhants1.unhinge.ui.component.Material3SettingsItem
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
    var aiTemperature by rememberSaveable { mutableStateOf(prefs.aiTemperature) }
    var aiTopP by rememberSaveable { mutableStateOf(prefs.aiTopP) }
    var aiMaxTokens by rememberSaveable { mutableStateOf(prefs.aiMaxTokens) }

    val aiProviders =
        mapOf(
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
                if (it == "custom_input") "Custom" else it
            },
        )
    }

    if (showCustomModelInput) {
        TextFieldDialog(
            title = { Text(stringResource(R.string.ai_model)) },
            icon = { Icon(Icons.Outlined.Tune, null) },
            initialTextFieldValue = TextFieldValue(text = openRouterModel),
            onDone = {
                openRouterModel = it
                prefs.openRouterModel = it
                prefs.saveToFallbackStorageAsync(context)
                showCustomModelInput = false
            },
            onDismiss = { showCustomModelInput = false },
        )
    }



    if (showSystemPromptDialog) {
        TextFieldDialog(
            title = { Text("System prompt") },
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
                            Text("Reset to default")
                        }
                    }
                }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(16.dp))

        // Activation Group
        Material3SettingsGroup(
            title = "ACTIVATION",
            items = listOf(
                Material3SettingsItem(
                    imageVector = Icons.Outlined.TouchApp,
                    title = { Text("Floating Action Button on Hinge") },
                    description = { Text("Display draggable AI assistant button over Hinge to generate candidate prompt replies") },
                    trailingContent = {
                        Switch(
                            checked = prefs.showHostAppFab,
                            onCheckedChange = { prefs.showHostAppFab = it }
                        )
                    }
                )
            )
        )

        Spacer(modifier = Modifier.height(27.dp))

        // AI Provider Group
        Material3SettingsGroup(
            title = stringResource(R.string.ai_provider),
            items =
                listOf(
                    Material3SettingsItem(
                        imageVector = Icons.Outlined.Explore,
                        title = { Text(stringResource(R.string.ai_provider)) },
                        description = { Text(aiProvider) },
                        onClick = { showProviderDialog = true },
                        trailingContent = {
                            IconButton(onClick = { showProviderHelpDialog = true }) {
                                Icon(
                                    Icons.Outlined.Info,
                                    contentDescription = stringResource(R.string.ai_provider_help),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        },
                    ),
                    if (aiProvider == "Custom") {
                        Material3SettingsItem(
                            imageVector = Icons.Outlined.Link,
                            title = { Text(stringResource(R.string.ai_base_url)) },
                            description = { Text(openRouterBaseUrl.ifBlank { stringResource(R.string.ai_not_set) }) },
                            onClick = { showBaseUrlDialog = true },
                        )
                    } else {
                        null
                    },
                ).filterNotNull(),
        )

        Spacer(modifier = Modifier.height(27.dp))

        // API Credentials Group
        Material3SettingsGroup(
            title = stringResource(R.string.ai_setup_guide),
            items =
                listOf(
                    Material3SettingsItem(
                        imageVector = Icons.Outlined.Key,
                        title = { Text(stringResource(R.string.ai_api_key)) },
                        description = {
                            Text(
                                if (openRouterApiKey.isNotEmpty()) {
                                    "•".repeat(minOf(openRouterApiKey.length, 8))
                                } else {
                                    stringResource(R.string.ai_not_set)
                                },
                            )
                        },
                        onClick = { showApiKeyDialog = true },
                    ),
                    Material3SettingsItem(
                        imageVector = Icons.Outlined.Tune,
                        title = { Text(stringResource(R.string.ai_model)) },
                        description = { Text(openRouterModel.ifBlank { stringResource(R.string.ai_not_set) }) },
                        onClick = { showModelDialog = true },
                    ),
                ),
        )



        Spacer(modifier = Modifier.height(27.dp))

        // Model Parameters Group
        Material3SettingsGroup(
            title = "MODEL PARAMETERS",
            items = listOf(
                Material3SettingsItem(
                    imageVector = Icons.Outlined.Tune,
                    title = { Text("Temperature") },
                    description = { Text(String.format(Locale.US, "%.2f", aiTemperature)) },
                    content = {
                        Slider(
                            value = aiTemperature,
                            onValueChange = {
                                aiTemperature = ((it / 0.05f).roundToInt() * 0.05f).coerceIn(0f, 1.5f)
                            },
                            valueRange = 0f..1.5f,
                            onValueChangeFinished = {
                                prefs.aiTemperature = aiTemperature
                                prefs.saveToFallbackStorageAsync(context)
                            }
                        )
                    }
                ),
                Material3SettingsItem(
                    imageVector = Icons.Outlined.Tune,
                    title = { Text("Top P") },
                    description = { Text(String.format(Locale.US, "%.2f", aiTopP)) },
                    content = {
                        Slider(
                            value = aiTopP,
                            onValueChange = {
                                aiTopP = ((it / 0.05f).roundToInt() * 0.05f).coerceIn(0.1f, 1f)
                            },
                            valueRange = 0.1f..1f,
                            onValueChangeFinished = {
                                prefs.aiTopP = aiTopP
                                prefs.saveToFallbackStorageAsync(context)
                            }
                        )
                    }
                ),
                Material3SettingsItem(
                    imageVector = Icons.Outlined.Tune,
                    title = { Text("Max Tokens") },
                    description = { Text(aiMaxTokens.toString()) },
                    content = {
                        Slider(
                            value = aiMaxTokens.toFloat(),
                            onValueChange = {
                                aiMaxTokens = ((it / 50f).roundToInt() * 50).coerceIn(50, 1000)
                            },
                            valueRange = 50f..1000f,
                            onValueChangeFinished = {
                                prefs.aiMaxTokens = aiMaxTokens
                                prefs.saveToFallbackStorageAsync(context)
                            }
                        )
                    }
                )
            )
        )

        Spacer(modifier = Modifier.height(27.dp))

        // Prompt Instructions
        Material3SettingsGroup(
            title = "PROMPT INSTRUCTIONS",
            items = listOf(
                Material3SettingsItem(
                    imageVector = Icons.Outlined.Edit,
                    title = { Text("System prompt") },
                    description = {
                        Text(
                            if (aiSystemPrompt.isNotBlank()) {
                                aiSystemPrompt.take(60).let {
                                    if (aiSystemPrompt.length > 60) "$it…" else it
                                }
                            } else {
                                "Default"
                            }
                        )
                    },
                    onClick = { showSystemPromptDialog = true }
                )
            )
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

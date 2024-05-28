package dev.chungjungsoo.gptmobile.presentation.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.chungjungsoo.gptmobile.data.datastore.SettingDataSource
import dev.chungjungsoo.gptmobile.data.dto.Platform
import dev.chungjungsoo.gptmobile.data.model.ApiType
import dev.chungjungsoo.gptmobile.presentation.common.Route
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SetupViewModel @Inject constructor(private val settingDataSource: SettingDataSource) : ViewModel() {

    // LinkedHashSet should be used to guarantee item order
    val openaiModels = linkedSetOf("gpt-4o", "gpt-4-turbo", "gpt-4", "gpt-3.5-turbo")
    val anthropicModels = linkedSetOf("claude-3-opus-20240229", "claude-3-sonnet-20240229", "claude-3-haiku-20240307")
    val googleModels = linkedSetOf("gemini-1.5-pro-latest", "gemini-1.5-flash-latest", "gemini-1.0-pro")

    private val _platformState = MutableStateFlow(
        listOf(
            Platform(ApiType.OPENAI),
            Platform(ApiType.ANTHROPIC),
            Platform(ApiType.GOOGLE)
        )
    )
    val platformState: StateFlow<List<Platform>> = _platformState.asStateFlow()

    fun updateCheckedState(platform: Platform) {
        val index = _platformState.value.indexOf(platform)

        if (index >= 0) {
            _platformState.update {
                it.mapIndexed { i, p ->
                    if (index == i) {
                        p.copy(selected = p.selected.not())
                    } else {
                        p
                    }
                }
            }
        }
    }

    fun updateToken(platform: Platform, token: String) {
        val index = _platformState.value.indexOf(platform)

        if (index >= 0) {
            _platformState.update {
                it.mapIndexed { i, p ->
                    if (index == i) {
                        p.copy(token = token.ifBlank { null })
                    } else {
                        p
                    }
                }
            }
        }
    }

    fun updateModel(apiType: ApiType, model: String) {
        val index = _platformState.value.indexOfFirst { it.name == apiType }
        val models = when (apiType) {
            ApiType.OPENAI -> openaiModels
            ApiType.ANTHROPIC -> anthropicModels
            ApiType.GOOGLE -> googleModels
        }

        if (index >= 0) {
            _platformState.update {
                it.mapIndexed { i, p ->
                    if (index == i) {
                        p.copy(model = if (model in models) model else null)
                    } else {
                        p
                    }
                }
            }
        }
    }

    fun saveCheckedState() {
        _platformState.value.forEach { platform ->
            viewModelScope.launch {
                settingDataSource.updateStatus(platform.name, platform.selected)
            }
        }
    }

    fun saveTokenState() {
        _platformState.value.filter { it.selected && it.token != null }.forEach { platform ->
            viewModelScope.launch {
                settingDataSource.updateToken(platform.name, platform.token!!)
            }
        }
    }

    fun saveModelState() {
        _platformState.value.filter { it.selected && it.token != null && it.model != null }.forEach { platform ->
            viewModelScope.launch {
                settingDataSource.updateModel(platform.name, platform.model!!)
            }
        }
    }

    fun setModel(apiType: ApiType, defaultModelIndex: Int): String {
        return platformState.value.find { it.name == apiType }?.model ?: setDefaultModel(apiType, defaultModelIndex)
    }

    fun getNextSetupRoute(currentRoute: String?): String {
        val steps = listOf(
            Route.SELECT_PLATFORM,
            Route.TOKEN_INPUT,
            Route.OPENAI_MODEL_SELECT,
            Route.ANTHROPIC_MODEL_SELECT,
            Route.GOOGLE_MODEL_SELECT,
            Route.SETUP_COMPLETE
        )
        val commonSteps = setOf(Route.SELECT_PLATFORM, Route.TOKEN_INPUT, Route.SETUP_COMPLETE)
        val platformStep = mapOf(
            Route.OPENAI_MODEL_SELECT to ApiType.OPENAI,
            Route.ANTHROPIC_MODEL_SELECT to ApiType.ANTHROPIC,
            Route.GOOGLE_MODEL_SELECT to ApiType.GOOGLE
        )

        val currentIndex = steps.indexOfFirst { it == currentRoute }
        val enabledPlatform = platformState.value.filter { it.selected }.map { it.name }.toSet()
        val remainingSteps = steps.filterIndexed { index, setupStep ->
            index > currentIndex &&
                (setupStep in commonSteps || platformStep[setupStep] in enabledPlatform)
        }

        if (remainingSteps.isEmpty()) {
            // Setup Complete
            return Route.CHAT_LIST
        }

        return remainingSteps.first()
    }

    fun setDefaultModel(apiType: ApiType, defaultModelIndex: Int): String {
        val modelList = when (apiType) {
            ApiType.OPENAI -> openaiModels
            ApiType.ANTHROPIC -> anthropicModels
            ApiType.GOOGLE -> googleModels
        }.toList()

        val model = modelList[defaultModelIndex]
        updateModel(apiType, model)

        return model
    }
}

package com.visualagent.hyperos.agent

import android.content.Context
import com.visualagent.hyperos.core.CoordinateMapper
import com.visualagent.hyperos.core.ScreenCaptureManager
import com.visualagent.hyperos.core.TouchInjector
import com.visualagent.hyperos.core.VisualAgentAccessibilityService
import com.visualagent.hyperos.ui.sound.McSoundManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object AgentRunner {

    private const val PREFS_NAME = "visual_agent_prefs"
    private const val KEY_API_KEY = "gemini_api_key"
    private const val KEY_MODEL_ID = "gemini_model_id"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val geminiClient = GeminiComputerUseClient()
    private var currentTaskJob: Job? = null

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatUiMessage>>(emptyList())
    val messages: StateFlow<List<ChatUiMessage>> = _messages.asStateFlow()

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _selectedModel = MutableStateFlow(GeminiComputerUseClient.DEFAULT_MODEL)
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    // 最近一次失败时的按需诊断建议（平时隐藏，仅在操作未达成目标或缺失权限时可点击查看）
    private val _lastDiagnosticHint = MutableStateFlow<String?>(null)
    val lastDiagnosticHint: StateFlow<String?> = _lastDiagnosticHint.asStateFlow()

    fun initPrefs(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _apiKey.value = prefs.getString(KEY_API_KEY, "") ?: ""
        _selectedModel.value = prefs.getString(KEY_MODEL_ID, GeminiComputerUseClient.DEFAULT_MODEL)
            ?: GeminiComputerUseClient.DEFAULT_MODEL
    }

    fun setApiKey(context: Context, key: String) {
        val clean = key.trim()
        _apiKey.value = clean
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_API_KEY, clean).apply()
    }

    fun setSelectedModel(context: Context, modelId: String) {
        val clean = modelId.trim().ifBlank { GeminiComputerUseClient.DEFAULT_MODEL }
        _selectedModel.value = clean
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_MODEL_ID, clean).apply()
    }

    fun clearDiagnosticHint() {
        _lastDiagnosticHint.value = null
    }

    fun appendMessage(msg: ChatUiMessage) {
        _messages.value = (_messages.value + msg).takeLast(120)
    }

    fun cancelCurrentTask(reason: String = "任务已手动停止") {
        if (currentTaskJob?.isActive == true) {
            currentTaskJob?.cancel(CancellationException(reason))
            _isRunning.value = false
            McSoundManager.playError()
            appendMessage(
                ChatUiMessage(
                    role = ChatUiMessage.Role.SYSTEM_LOG,
                    content = "⏹ $reason",
                    isSuccess = false
                )
            )
        }
    }

    fun startObjective(context: Context, userPrompt: String, onTriggerVoiceCall: () -> Unit) {
        val appContext = context.applicationContext
        _lastDiagnosticHint.value = null
        appendMessage(ChatUiMessage(role = ChatUiMessage.Role.USER, content = userPrompt))

        if (userPrompt.contains("跟我通话") || userPrompt.contains("语音通话") || userPrompt.contains("Live通话")) {
            McSoundManager.playSuccess()
            appendMessage(
                ChatUiMessage(
                    role = ChatUiMessage.Role.SYSTEM_LOG,
                    content = "🎙 正在连接 Gemini 3.8 Live 实时语音..."
                )
            )
            onTriggerVoiceCall()
            return
        }

        cancelCurrentTask("切换至新指令")
        _isRunning.value = true
        McSoundManager.playClick()

        currentTaskJob = scope.launch {
            val historyLog = mutableListOf<String>()
            var stepCount = 0
            val maxSteps = 15

            try {
                while (isActive && stepCount < maxSteps) {
                    stepCount++
                    val frame = ScreenCaptureManager.getLatestFrame()
                    val uiTree = VisualAgentAccessibilityService.instance?.dumpCompactUiTreeSummary()
                        ?: "[无障碍未开启]"
                    val fgPkg = LocalToolRegistry.getForegroundApp()

                    val planResult = geminiClient.planNextStep(
                        apiKey = _apiKey.value,
                        selectedModel = _selectedModel.value,
                        userObjective = userPrompt,
                        base64JpegScreenshot = frame?.base64Jpeg,
                        uiTreeSummary = uiTree,
                        foregroundApp = fgPkg,
                        historyLog = historyLog
                    )

                    val plan = planResult.getOrNull()
                    if (plan == null) {
                        val err = planResult.exceptionOrNull()?.message ?: "网络或接口异常"
                        McSoundManager.playError()
                        appendMessage(
                            ChatUiMessage(
                                role = ChatUiMessage.Role.SYSTEM_LOG,
                                content = "❌ $err",
                                isSuccess = false
                            )
                        )
                        _lastDiagnosticHint.value = buildDiagnosticForError(err, frame != null)
                        break
                    }

                    appendMessage(
                        ChatUiMessage(
                            role = ChatUiMessage.Role.AGENT_THOUGHT,
                            content = plan.thought
                        )
                    )

                    if (plan.objective_fulfilled || plan.action.equals("none", ignoreCase = true)) {
                        McSoundManager.playSuccess()
                        if (stepCount > 1) {
                            appendMessage(
                                ChatUiMessage(
                                    role = ChatUiMessage.Role.SYSTEM_LOG,
                                    content = "✅ 已完成操作（共 ${stepCount - 1} 步）"
                                )
                            )
                        }
                        break
                    }

                    val execSummary = executeStep(appContext, plan)
                    historyLog.add("Step $stepCount [${plan.action}]: $execSummary")
                    delay(1000L)
                }
            } catch (ce: CancellationException) {
                throw ce
            } finally {
                _isRunning.value = false
            }
        }
    }

    private fun buildDiagnosticForError(err: String, hasScreenFrame: Boolean): String {
        return buildString {
            when {
                err.contains("HTTP 404") -> {
                    appendLine("• 当前模型不可用或已停用：请在【设置】中切换为官方最新推荐的「Gemini 3.8 Flash」或「Gemini 3.5 Flash-Lite」。")
                }
                err.contains("HTTP 400") || err.contains("API_KEY_INVALID") -> {
                    appendLine("• API Key 格式无效：请确认填入的是 Google AI Studio 生成的 AIzaSy... 格式密钥。")
                }
                err.contains("connection closed") || err.contains("timeout") || err.contains("Unable to resolve host") -> {
                    appendLine("• 网络连接中断：请确认手机网络可正常访问 Google Gemini 接口服务。")
                }
            }
            if (!hasScreenFrame) {
                appendLine("• 尚未开启屏幕画面捕获：若需让 AI 看屏幕操作，请在【设置】中点击「开启录屏」并选择「录制整个屏幕」。")
            }
            if (TouchInjector.activeChannel.value == TouchInjector.InjectionChannel.UNAVAILABLE) {
                appendLine("• 尚未开启触控权限：请在【设置】中点击「开启无障碍」或「Shizuku」，否则 AI 无法代你点击屏幕。")
            }
        }.trim().ifEmpty { "请检查【设置】中的模型选择、API Key 与权限开关状态。" }
    }

    private suspend fun executeStep(context: Context, plan: AgentStepPlan): String {
        return when (plan.action.lowercase()) {
            "click" -> {
                val pt = CoordinateMapper.toPhysicalPoint(context, plan.normalized_x, plan.normalized_y)
                val ok = TouchInjector.tapNormalized(context, plan.normalized_x, plan.normalized_y)
                if (!ok) {
                    _lastDiagnosticHint.value = "点击未生效：请先开启「无障碍服务」或激活「Shizuku」（若使用 Shizuku，需在开发者选项中开启“USB 调试（安全设置）”）。"
                }
                val coordDesc = "(${plan.normalized_x}, ${plan.normalized_y}) -> (${pt.x}, ${pt.y})"
                appendMessage(
                    ChatUiMessage(
                        role = ChatUiMessage.Role.TOOL_CALL,
                        content = if (ok) "点击屏幕 $coordDesc" else "点击失败（未授予触控权限）",
                        toolName = "click",
                        isSuccess = ok
                    )
                )
                "click $coordDesc success=$ok"
            }

            "type" -> {
                val pt = CoordinateMapper.toPhysicalPoint(context, plan.normalized_x, plan.normalized_y)
                val ok = TouchInjector.inputTextAtNormalized(
                    context,
                    plan.normalized_x,
                    plan.normalized_y,
                    plan.text
                )
                if (!ok) {
                    _lastDiagnosticHint.value = "输入文本未生效：请确认已开启「无障碍服务」或激活「Shizuku」。"
                }
                appendMessage(
                    ChatUiMessage(
                        role = ChatUiMessage.Role.TOOL_CALL,
                        content = "输入: \"${plan.text}\"",
                        toolName = "type",
                        isSuccess = ok
                    )
                )
                "type '${plan.text}' at (${pt.x},${pt.y}) success=$ok"
            }

            "scroll" -> {
                val ok = TouchInjector.swipeNormalized(
                    context,
                    plan.normalized_x,
                    plan.normalized_y,
                    plan.end_normalized_x,
                    plan.end_normalized_y
                )
                if (!ok) {
                    _lastDiagnosticHint.value = "滑动未生效：请确认已开启「无障碍服务」或激活「Shizuku」。"
                }
                appendMessage(
                    ChatUiMessage(
                        role = ChatUiMessage.Role.TOOL_CALL,
                        content = "滑动屏幕",
                        toolName = "scroll",
                        isSuccess = ok
                    )
                )
                "scroll success=$ok"
            }

            "open_app" -> {
                val res = LocalToolRegistry.openApp(context, plan.package_name)
                val ok = !res.startsWith("ERROR")
                appendMessage(
                    ChatUiMessage(
                        role = ChatUiMessage.Role.TOOL_CALL,
                        content = "打开应用: ${plan.package_name}",
                        toolName = "open_app",
                        isSuccess = ok
                    )
                )
                res
            }

            "shell_exec" -> {
                val res = LocalToolRegistry.shellExec(plan.shell_command)
                val ok = !res.startsWith("ERROR") && !res.startsWith("REJECTED")
                if (!ok) {
                    _lastDiagnosticHint.value = "Shell 命令需要 Shizuku 权限：请先启动并授权 Shizuku。"
                }
                appendMessage(
                    ChatUiMessage(
                        role = ChatUiMessage.Role.TOOL_CALL,
                        content = res,
                        toolName = "shell_exec",
                        isSuccess = ok
                    )
                )
                res
            }

            "get_foreground_app" -> {
                val pkg = LocalToolRegistry.getForegroundApp()
                "foreground_app=$pkg"
            }

            "wait" -> {
                LocalToolRegistry.waitMs(plan.wait_ms)
            }

            else -> "Unknown action: ${plan.action}"
        }
    }
}

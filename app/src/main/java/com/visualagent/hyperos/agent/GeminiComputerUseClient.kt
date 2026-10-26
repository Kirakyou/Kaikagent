package com.visualagent.hyperos.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class GeminiModelOption(
    val id: String,
    val title: String,
    val badge: String
)

class GeminiComputerUseClient {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    companion object {
        // 根据 Google Gemini API 最新政策更新：gemini-2.5-flash 对新账号返回 404，官方推荐默认使用 gemini-3.8-flash / gemini-3.5-flash-lite
        const val DEFAULT_MODEL = "gemini-3.8-flash"

        val RECOMMENDED_MODELS = listOf(
            GeminiModelOption(
                id = "gemini-3.8-flash",
                title = "Gemini 3.8 Flash",
                badge = "官方首选"
            ),
            GeminiModelOption(
                id = "gemini-3.5-flash-lite",
                title = "Gemini 3.5 Flash-Lite",
                badge = "极速轻量"
            ),
            GeminiModelOption(
                id = "gemini-3.6-flash",
                title = "Gemini 3.6 Flash",
                badge = "稳定均衡"
            ),
            GeminiModelOption(
                id = "gemini-3-flash-preview",
                title = "Gemini 3.0 Flash",
                badge = "预览兼容"
            ),
            GeminiModelOption(
                id = "gemini-4-argon",
                title = "Gemini 4 Argon",
                badge = "旗舰推理"
            )
        )

        private val FALLBACK_CHAIN = listOf(
            "gemini-3.8-flash",
            "gemini-3.5-flash-lite",
            "gemini-3.6-flash",
            "gemini-3-flash-preview"
        )

        private val SYSTEM_PROMPT = """
            You are VisualAgent, an Android visual AI assistant.
            If the user is just chatting or asking a question (e.g. "你好", "为什么会这样") without needing screen manipulation, set "objective_fulfilled": true, "action": "none", and put your friendly reply in "thought".
            CRITICAL COORDINATE RULE FOR UI ACTIONS:
            - All screen coordinates (normalized_x, normalized_y, end_normalized_x, end_normalized_y) MUST use a 0 to 999 normalized grid!
            - (0, 0) is top-left, (999, 999) is bottom-right. Never output raw pixel coordinates.
            
            AVAILABLE ACTIONS:
            - "click": tap at (normalized_x, normalized_y)
            - "type": tap at (normalized_x, normalized_y) and input `text`
            - "scroll": swipe from (normalized_x, normalized_y) to (end_normalized_x, end_normalized_y)
            - "wait": wait for `wait_ms` milliseconds
            - "open_app": launch app by `package_name`
            - "shell_exec": run safe shell command in `shell_command`
            - "get_foreground_app": query current foreground package name
            - "none": use when `objective_fulfilled` is true or replying to chat.
            
            Respond strictly with a single JSON object:
            {
              "thought": "your reply to the user or concise reasoning about the current step",
              "objective_fulfilled": boolean,
              "action": "click|type|scroll|wait|open_app|shell_exec|get_foreground_app|none",
              "normalized_x": 0-999,
              "normalized_y": 0-999,
              "end_normalized_x": 0-999,
              "end_normalized_y": 0-999,
              "text": "",
              "package_name": "",
              "shell_command": "",
              "wait_ms": 1000
            }
        """.trimIndent()
    }

    suspend fun planNextStep(
        apiKey: String,
        selectedModel: String,
        userObjective: String,
        base64JpegScreenshot: String?,
        uiTreeSummary: String,
        foregroundApp: String,
        historyLog: List<String>
    ): Result<AgentStepPlan> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("请先在【设置】页填入 Gemini API Key"))
        }

        val modelsToTry = (listOf(selectedModel.ifBlank { DEFAULT_MODEL }) + FALLBACK_CHAIN).distinct()
        var lastErrorMsg = ""

        for (modelId in modelsToTry) {
            val res = callGenerateContent(
                apiKey = cleanKey,
                modelId = modelId,
                userObjective = userObjective,
                base64JpegScreenshot = base64JpegScreenshot,
                uiTreeSummary = uiTreeSummary,
                foregroundApp = foregroundApp,
                historyLog = historyLog
            )
            if (res.isSuccess) {
                return@withContext res
            }
            val errText = res.exceptionOrNull()?.message.orEmpty()
            lastErrorMsg = errText
            if (!errText.contains("HTTP 404")) {
                break
            }
        }

        Result.failure(IllegalStateException(lastErrorMsg))
    }

    private fun callGenerateContent(
        apiKey: String,
        modelId: String,
        userObjective: String,
        base64JpegScreenshot: String?,
        uiTreeSummary: String,
        foregroundApp: String,
        historyLog: List<String>
    ): Result<AgentStepPlan> {
        val cleanModel = modelId.removePrefix("models/").trim()
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$cleanModel:generateContent?key=$apiKey"

        val partsArray = buildJsonArray {
            add(buildJsonObject {
                put("text", buildString {
                    appendLine("User Input / Objective: $userObjective")
                    appendLine("Foreground App: $foregroundApp")
                    if (historyLog.isNotEmpty()) {
                        appendLine("Recent Steps:\n${historyLog.takeLast(6).joinToString("\n")}")
                    }
                    appendLine("UI Tree:\n$uiTreeSummary")
                })
            })
            if (!base64JpegScreenshot.isNullOrBlank()) {
                add(buildJsonObject {
                    put("inline_data", buildJsonObject {
                        put("mime_type", "image/jpeg")
                        put("data", base64JpegScreenshot)
                    })
                })
            }
        }

        val payload = buildJsonObject {
            put("system_instruction", buildJsonObject {
                put("parts", buildJsonArray {
                    add(buildJsonObject { put("text", SYSTEM_PROMPT) })
                })
            })
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("parts", partsArray)
                })
            })
            put("generationConfig", buildJsonObject {
                put("temperature", 0.1)
                put("responseMimeType", "application/json")
            })
        }

        val request = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return Result.failure(
                        IllegalStateException("Gemini API ($cleanModel) HTTP ${response.code}: ${bodyStr.take(260)}")
                    )
                }
                val rootObj = json.parseToJsonElement(bodyStr).jsonObject
                val textContent = rootObj["candidates"]
                    ?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("content")
                    ?.jsonObject?.get("parts")
                    ?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("text")
                    ?.jsonPrimitive?.content
                    ?: return Result.failure(IllegalStateException("模型返回内容为空"))

                val stepPlan = json.decodeFromString(AgentStepPlan.serializer(), textContent)
                Result.success(stepPlan)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

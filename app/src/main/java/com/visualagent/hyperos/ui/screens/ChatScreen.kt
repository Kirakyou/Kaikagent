package com.visualagent.hyperos.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.visualagent.hyperos.R
import com.visualagent.hyperos.agent.AgentRunner
import com.visualagent.hyperos.agent.ChatUiMessage
import com.visualagent.hyperos.agent.GeminiLiveVoiceManager
import com.visualagent.hyperos.ui.theme.McButtonVariant
import com.visualagent.hyperos.ui.theme.McPanelContainer
import com.visualagent.hyperos.ui.theme.McPixelButton
import com.visualagent.hyperos.ui.theme.mcBevelPanel
import com.visualagent.hyperos.ui.theme.mcTiledTextureBackground

@Composable
fun ChatScreen(onOpenSettings: () -> Unit = {}) {
    val context = LocalContext.current
    val messages by AgentRunner.messages.collectAsState()
    val isRunning by AgentRunner.isRunning.collectAsState()
    val isLiveVoice by GeminiLiveVoiceManager.isLiveActive.collectAsState()
    val apiKey by AgentRunner.apiKey.collectAsState()
    val selectedModel by AgentRunner.selectedModel.collectAsState()
    val diagnosticHint by AgentRunner.lastDiagnosticHint.collectAsState()

    var inputPrompt by remember { mutableStateOf("") }
    var showDiagnosticModal by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 顶部状态栏（显示当前模型与语音通话按钮）
        McPanelContainer(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            isLiveVoice -> "🎙 实时语音通话中"
                            isRunning -> "⚡ 正在执行任务..."
                            else -> "🟢 待命中 · 模型: $selectedModel"
                        },
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
                McPixelButton(
                    text = if (isLiveVoice) "挂断语音" else "语音通话",
                    variant = if (isLiveVoice) McButtonVariant.RED else McButtonVariant.GREEN,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    GeminiLiveVoiceManager.toggleVoiceSession(apiKey)
                }
            }
        }

        // 按需故障诊断按钮：仅当操作报错或未达成目标时才出现，平时完全隐藏！
        if (!diagnosticHint.isNullOrBlank()) {
            McPixelButton(
                text = "🔍 刚才的操作未完成，点击查看原因与解决步骤",
                variant = McButtonVariant.RED,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
            ) {
                showDiagnosticModal = true
            }
        }

        // 消息流列表（使用圆石材质平铺背景）
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .mcTiledTextureBackground(resId = R.drawable.mc_cobblestone, darkenAlpha = 0.58f)
                .mcBevelPanel(baseColor = Color.Transparent)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                MessageCard(msg)
            }
        }

        // 底部指令输入与发送/停止按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = inputPrompt,
                onValueChange = { inputPrompt = it },
                cursorBrush = SolidColor(Color.White),
                textStyle = TextStyle(
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                ),
                modifier = Modifier
                    .weight(1f)
                    .mcBevelPanel(baseColor = Color(0xFF18181A))
                    .padding(12.dp),
                decorationBox = { inner ->
                    if (inputPrompt.isEmpty()) {
                        Text(
                            text = "输入指令或聊天 (如: 打开设置 / 跟我通话)",
                            color = Color(0xFF888888),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                    inner()
                }
            )

            if (isRunning) {
                McPixelButton(
                    text = "停止",
                    variant = McButtonVariant.RED
                ) {
                    AgentRunner.cancelCurrentTask("已停止当前任务")
                }
            } else {
                McPixelButton(
                    text = "发送",
                    variant = McButtonVariant.GREEN
                ) {
                    val prompt = inputPrompt.trim()
                    if (prompt.isNotEmpty()) {
                        inputPrompt = ""
                        AgentRunner.startObjective(
                            context = context,
                            userPrompt = prompt,
                            onTriggerVoiceCall = {
                                GeminiLiveVoiceManager.startVoiceSession(apiKey)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showDiagnosticModal && !diagnosticHint.isNullOrBlank()) {
        Dialog(onDismissRequest = { showDiagnosticModal = false }) {
            McPanelContainer(
                modifier = Modifier.fillMaxWidth(),
                useCobblestone = true
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "🔍 为什么刚才的操作没成功？",
                        color = Color(0xFFFFD54F),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = diagnosticHint.orEmpty(),
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        McPixelButton(
                            text = "去设置页处理",
                            variant = McButtonVariant.GREEN,
                            modifier = Modifier.weight(1f)
                        ) {
                            showDiagnosticModal = false
                            onOpenSettings()
                        }
                        McPixelButton(
                            text = "关闭提示",
                            variant = McButtonVariant.GRAY,
                            modifier = Modifier.weight(1f)
                        ) {
                            showDiagnosticModal = false
                            AgentRunner.clearDiagnosticHint()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageCard(msg: ChatUiMessage) {
    val bgColor = when (msg.role) {
        ChatUiMessage.Role.USER -> Color(0xCC284A1E)
        ChatUiMessage.Role.AGENT_THOUGHT -> Color(0xCC2A2B2E)
        ChatUiMessage.Role.TOOL_CALL -> Color(0xCC1E3246)
        ChatUiMessage.Role.SYSTEM_LOG -> if (msg.isSuccess) Color(0xCC243036) else Color(0xCC521E1E)
    }

    val roleTitle = when (msg.role) {
        ChatUiMessage.Role.USER -> "我"
        ChatUiMessage.Role.AGENT_THOUGHT -> "AI"
        ChatUiMessage.Role.TOOL_CALL -> "执行动作 [${msg.toolName ?: ""}]"
        ChatUiMessage.Role.SYSTEM_LOG -> "系统提示"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .mcBevelPanel(baseColor = bgColor)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = roleTitle,
            color = Color(0xFFAED581),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp
        )
        Text(
            text = msg.content,
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp
        )
    }
}

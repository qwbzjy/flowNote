package com.wenbo.flownote.speech

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** 语音识别状态机。 */
sealed interface SpeechState {
    data object Idle : SpeechState

    /** 听写中，[partial] 为系统实时返回的部分结果（灰字预览）。 */
    data class Listening(val partial: String) : SpeechState

    /** 一次性错误提示，展示后由引擎自动回到 [Idle]。 */
    data class Error(val message: String) : SpeechState
}

/**
 * 语音转文字引擎抽象，便于 mock 与预览。
 *
 * 线程约定：start/stop/cancel 必须在主线程调用；finalResults 在主线程发射。
 */
interface SpeechToTextEngine {
    val state: StateFlow<SpeechState>

    /** 最终识别结果（一次性事件，replay=0）。 */
    val finalResults: SharedFlow<String>

    /** false 时 UI 应隐藏麦克风按钮（懒探测：创建失败或 ERROR_CLIENT 后置 false）。 */
    val isAvailable: StateFlow<Boolean>

    fun start(languageTag: String)

    /** 停止收音并等待最终结果（对应 stopListening）。 */
    fun stop()

    /** 放弃本次识别，立即回到 Idle。 */
    fun cancel()

    fun destroy()
}

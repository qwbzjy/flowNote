package com.wenbo.flownote.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android 内置 SpeechRecognizer 封装。
 *
 * - API 31+ 优先 createOnDeviceSpeechRecognizer（真离线）；
 * - API 26–30 回退系统识别服务（云端或厂商离线包，取决于设备）；
 * - 懒探测：不信任 isRecognitionAvailable 的误报，首次 start 时真创建，
 *   创建失败或收到 ERROR_CLIENT 后永久置 isAvailable=false，UI 隐藏麦克风按钮。
 */
class AndroidSpeechToTextEngine(context: Context) : SpeechToTextEngine, RecognitionListener {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow<SpeechState>(SpeechState.Idle)
    override val state: StateFlow<SpeechState> = _state.asStateFlow()

    private val _finalResults = MutableSharedFlow<String>(
        replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val finalResults: SharedFlow<String> = _finalResults.asSharedFlow()

    private val _isAvailable = MutableStateFlow(true)
    override val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    private var recognizer: SpeechRecognizer? = null

    override fun start(languageTag: String) {
        mainHandler.post {
            if (!_isAvailable.value) return@post
            val rec = obtainRecognizer() ?: run {
                markUnavailable()
                return@post
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            _state.value = SpeechState.Listening(partial = "")
            rec.startListening(intent)
        }
    }

    override fun stop() {
        mainHandler.post {
            when (val s = _state.value) {
                is SpeechState.Listening -> recognizer?.stopListening()
                else -> Unit
            }
        }
    }

    override fun cancel() {
        mainHandler.post {
            recognizer?.cancel()
            _state.value = SpeechState.Idle
        }
    }

    override fun destroy() {
        mainHandler.post {
            recognizer?.destroy()
            recognizer = null
            _state.value = SpeechState.Idle
        }
    }

    private fun obtainRecognizer(): SpeechRecognizer? {
        recognizer?.let { return it }
        val created: SpeechRecognizer? = try {
            if (Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
            } else {
                SpeechRecognizer.createSpeechRecognizer(appContext)
            }
        } catch (e: Exception) {
            null
        }
        created?.setRecognitionListener(this)
        recognizer = created
        return created
    }

    private fun markUnavailable() {
        _isAvailable.value = false
        _state.value = SpeechState.Idle
    }

    // ---- RecognitionListener（创建于主线程，回调亦在主线程） ----

    override fun onReadyForSpeech(params: Bundle?) = Unit

    override fun onBeginningOfSpeech() = Unit

    override fun onRmsChanged(rmsdB: Float) = Unit

    override fun onBufferReceived(buffer: ByteArray?) = Unit

    override fun onEndOfSpeech() = Unit

    override fun onError(error: Int) {
        when (error) {
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                emitError("没听清，再试一次")

            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                emitError("缺少麦克风权限")

            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> emitError("识别服务忙，请稍后再试")

            SpeechRecognizer.ERROR_CLIENT -> markUnavailable()

            else -> emitError("识别出错（代码 $error）")
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val text = partialResults
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            .orEmpty()
        if (_state.value is SpeechState.Listening) {
            _state.value = SpeechState.Listening(partial = text)
        }
    }

    override fun onResults(results: Bundle?) {
        val text = results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            .orEmpty()
        _state.value = SpeechState.Idle
        if (text.isNotBlank()) _finalResults.tryEmit(text)
    }

    override fun onEvent(eventType: Int, params: Bundle?) = Unit

    private fun emitError(message: String) {
        _state.value = SpeechState.Error(message)
        // 展示片刻后自动回到 Idle，供下一次收音
        mainHandler.postDelayed({
            if (_state.value is SpeechState.Error) _state.value = SpeechState.Idle
        }, ERROR_DISPLAY_MILLIS)
    }

    private companion object {
        const val ERROR_DISPLAY_MILLIS = 2000L
    }
}

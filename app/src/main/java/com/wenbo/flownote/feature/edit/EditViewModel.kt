package com.wenbo.flownote.feature.edit

import android.net.Uri
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wenbo.flownote.di.AppContainer
import com.wenbo.flownote.speech.SpeechState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

data class EditUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val content: TextFieldValue = TextFieldValue(""),
    val images: List<String> = emptyList(),
    val isImportingImage: Boolean = false,
)

class EditViewModel(
    private val container: AppContainer,
    private val memoId: Long,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditUiState())
    val uiState: StateFlow<EditUiState> = _uiState.asStateFlow()

    private val _events = Channel<String>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val _closed = Channel<Unit>(Channel.BUFFERED)
    val closed = _closed.receiveAsFlow()

    private val speechEngine = container.createSpeechEngine()
    val speechState: StateFlow<SpeechState> = speechEngine.state
    val speechAvailable: StateFlow<Boolean> = speechEngine.isAvailable
    val speechFinal = speechEngine.finalResults

    init {
        if (memoId > 0) {
            viewModelScope.launch {
                val memo = container.memoRepository.getMemo(memoId)
                if (memo == null) {
                    _events.send("笔记不存在或已删除")
                    _closed.trySend(Unit)
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isNew = false,
                            content = TextFieldValue(memo.content),
                            images = memo.images,
                        )
                    }
                }
            }
        } else {
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun updateContent(value: TextFieldValue) = _uiState.update { it.copy(content = value) }

    fun save() {
        val state = _uiState.value
        if (state.isLoading) return
        val text = state.content.text.trim()
        if (text.isEmpty() && state.images.isEmpty()) {
            if (state.isNew) {
                // 空白新笔记：直接退出，不保存
                _closed.trySend(Unit)
            } else {
                _events.trySend("内容为空，无法保存")
            }
            return
        }
        viewModelScope.launch {
            container.memoRepository.saveMemo(
                id = if (state.isNew) null else memoId,
                content = text,
                imageNames = state.images,
            )
            _closed.trySend(Unit)
        }
    }

    fun delete() {
        if (_uiState.value.isNew) {
            _closed.trySend(Unit)
            return
        }
        viewModelScope.launch {
            container.memoRepository.deleteMemo(memoId)
            _closed.trySend(Unit)
        }
    }

    // ---- 图片 ----

    fun onImagesPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isImportingImage = true) }
            val imported = mutableListOf<String>()
            uris.forEach { uri ->
                val name = container.imageStore.importFrom(uri)
                if (name != null) imported += name else _events.send("有图片导入失败")
            }
            _uiState.update { it.copy(images = it.images + imported, isImportingImage = false) }
        }
    }

    fun removeImage(name: String) {
        // 编辑中仅从列表移除（文件在保存/删除路径统一清理），未保存退出则保留文件与数据库引用
        _uiState.update { it.copy(images = it.images - name) }
    }

    // ---- 语音 ----

    fun startSpeech() {
        speechEngine.start(Locale.getDefault().toLanguageTag())
    }

    fun stopSpeech() = speechEngine.stop()

    fun insertSpeechText(text: String) {
        _uiState.update { state ->
            val current = state.content
            val start = current.selection.min.coerceIn(0, current.text.length)
            val end = current.selection.max.coerceIn(0, current.text.length)
            val builder = StringBuilder(current.text).replace(start, end, text)
            state.copy(content = TextFieldValue(builder.toString(), TextRange(start + text.length)))
        }
    }

    override fun onCleared() {
        speechEngine.destroy()
        super.onCleared()
    }
}

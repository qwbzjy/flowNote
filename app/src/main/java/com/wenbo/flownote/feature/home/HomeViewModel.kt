package com.wenbo.flownote.feature.home

import android.net.Uri
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wenbo.flownote.data.backup.ExportReport
import com.wenbo.flownote.data.backup.ImportReport
import com.wenbo.flownote.di.AppContainer
import com.wenbo.flownote.domain.model.Memo
import com.wenbo.flownote.domain.model.MemoFilter
import com.wenbo.flownote.domain.model.TagWithCount
import com.wenbo.flownote.speech.SpeechState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

data class HomeUiState(
    val isLoading: Boolean = true,
    val memos: List<Memo> = emptyList(),
    val filter: MemoFilter = MemoFilter.All,
    val tags: List<TagWithCount> = emptyList(),
    val totalCount: Int = 0,
    val draft: TextFieldValue = TextFieldValue(""),
    val draftImages: List<String> = emptyList(),
    val isImportingImage: Boolean = false,
    val isExporting: Boolean = false,
    val isImporting: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val container: AppContainer,
) : ViewModel() {

    private val memoRepository = container.memoRepository
    private val tagRepository = container.tagRepository

    private val filter = MutableStateFlow<MemoFilter>(MemoFilter.All)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _events = Channel<String>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    // SpeechRecognizer 构造是惰性的（首次 start 才绑定服务），VM 初始化成本低
    private val speechEngine = container.createSpeechEngine()
    val speechState: StateFlow<SpeechState> = speechEngine.state
    val speechAvailable: StateFlow<Boolean> = speechEngine.isAvailable
    val speechFinal = speechEngine.finalResults

    init {
        viewModelScope.launch {
            filter.flatMapLatest { f -> memoRepository.observeMemos(f) }
                .collect { memos -> _uiState.update { it.copy(memos = memos, isLoading = false) } }
        }
        viewModelScope.launch {
            combine(tagRepository.observeTags(), memoRepository.observeTotalCount()) { tags, count ->
                tags to count
            }.collect { (tags, count) ->
                _uiState.update { it.copy(tags = tags, totalCount = count) }
            }
        }
    }

    // ---- 输入 ----

    fun updateDraft(value: TextFieldValue) = _uiState.update { it.copy(draft = value) }

    fun submitDraft() {
        val state = _uiState.value
        // 导入覆盖期间禁止新增，避免与恢复事务交错
        if (state.isImporting) return
        val text = state.draft.text.trim()
        if (text.isEmpty() && state.draftImages.isEmpty()) return
        val images = state.draftImages
        viewModelScope.launch {
            memoRepository.saveMemo(id = null, content = text, imageNames = images)
            _uiState.update { it.copy(draft = TextFieldValue(""), draftImages = emptyList()) }
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
            _uiState.update { it.copy(draftImages = it.draftImages + imported, isImportingImage = false) }
        }
    }

    fun removeDraftImage(name: String) {
        viewModelScope.launch {
            container.imageStore.deleteAll(listOf(name))
            _uiState.update { it.copy(draftImages = it.draftImages - name) }
        }
    }

    // ---- 语音 ----

    fun startSpeech() {
        speechEngine.start(Locale.getDefault().toLanguageTag())
    }

    fun stopSpeech() = speechEngine.stop()

    fun cancelSpeech() = speechEngine.cancel()

    /** 把识别结果插入光标处（中文不加空格）。 */
    fun insertSpeechText(text: String) {
        _uiState.update { state ->
            val current = state.draft
            val start = current.selection.min.coerceIn(0, current.text.length)
            val end = current.selection.max.coerceIn(0, current.text.length)
            val builder = StringBuilder(current.text).replace(start, end, text)
            state.copy(draft = TextFieldValue(builder.toString(), TextRange(start + text.length)))
        }
    }

    // ---- 标签筛选 ----

    fun selectTag(tagId: Long, name: String) {
        filter.value = MemoFilter.ByTag(tagId, name)
        _uiState.update { it.copy(filter = MemoFilter.ByTag(tagId, name)) }
    }

    fun clearFilter() {
        filter.value = MemoFilter.All
        _uiState.update { it.copy(filter = MemoFilter.All) }
    }

    // ---- 备份 ----

    /** [uri] 为 null 表示用户在文件选择器中取消。 */
    fun onExportTarget(uri: Uri?) {
        if (uri == null) return
        val state = _uiState.value
        if (state.isExporting || state.isImporting) return
        _uiState.update { it.copy(isExporting = true) }
        viewModelScope.launch {
            val report = container.backupManager.exportTo(
                openOutput = { container.openBackupOutput(uri) },
            )
            _uiState.update { it.copy(isExporting = false) }
            when (report) {
                is ExportReport.Success -> {
                    val skipped = if (report.skippedImageCount > 0) {
                        "，${report.skippedImageCount} 张图片缺失已跳过"
                    } else ""
                    _events.send("备份已导出（共 ${report.memoCount} 条笔记$skipped）")
                }
                is ExportReport.Failure -> _events.send(report.reason)
            }
        }
    }

    /** [uri] 为 null 表示用户在文件选择器中取消。 */
    fun onImportTarget(uri: Uri?) {
        if (uri == null) return
        val state = _uiState.value
        if (state.isExporting || state.isImporting) return
        _uiState.update { it.copy(isImporting = true) }
        viewModelScope.launch {
            val report = container.backupManager.importFrom(
                openInput = { container.openBackupInput(uri) },
            )
            _uiState.update { it.copy(isImporting = false) }
            when (report) {
                is ImportReport.Success -> {
                    // 旧筛选的 tagId 在恢复后可能不存在，回到全部笔记
                    clearFilter()
                    val missing = if (report.missingImageCount > 0) {
                        "，${report.missingImageCount} 张图片在备份中缺失"
                    } else ""
                    _events.send("导入完成（共 ${report.memoCount} 条笔记$missing）")
                }
                is ImportReport.Failure -> _events.send(report.reason)
            }
        }
    }

    // ---- 删除 ----

    fun deleteMemo(id: Long) {
        viewModelScope.launch {
            memoRepository.deleteMemo(id)
            _events.send("已删除")
        }
    }

    // ---- 置顶 ----

    fun togglePin(memo: Memo) {
        viewModelScope.launch {
            memoRepository.setPinned(memo.id, !memo.pinned)
        }
    }

    override fun onCleared() {
        speechEngine.destroy()
        super.onCleared()
    }
}

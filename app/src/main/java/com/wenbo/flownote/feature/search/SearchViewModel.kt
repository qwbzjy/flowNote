package com.wenbo.flownote.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wenbo.flownote.di.AppContainer
import com.wenbo.flownote.domain.model.Memo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val results: List<Memo> = emptyList(),
    val searched: Boolean = false,
)

class SearchViewModel(
    private val container: AppContainer,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(results = emptyList(), searched = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(250) // 防抖
            val results = container.memoRepository.search(query)
            _uiState.update { it.copy(results = results, searched = true) }
        }
    }

    /** 置顶切换：搜索是一次性查询（非 Flow），本地同步更新结果列表。 */
    fun togglePin(memo: Memo) {
        viewModelScope.launch {
            container.memoRepository.setPinned(memo.id, !memo.pinned)
            _uiState.update { state ->
                state.copy(results = state.results.map {
                    if (it.id == memo.id) it.copy(pinned = !memo.pinned) else it
                })
            }
        }
    }
}

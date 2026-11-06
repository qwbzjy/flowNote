package com.wenbo.flownote.feature.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wenbo.flownote.di.AppContainer
import com.wenbo.flownote.domain.model.TagWithCount
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TagManagerViewModel(
    private val container: AppContainer,
) : ViewModel() {

    val tags: StateFlow<List<TagWithCount>> = container.tagRepository.observeTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _events = Channel<String>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** 重命名；目标名已存在时语义为合并。 */
    fun rename(tag: TagWithCount, newName: String) {
        val name = newName.trim().trimStart('#')
        if (name.isEmpty()) {
            _events.trySend("标签名不能为空")
            return
        }
        viewModelScope.launch {
            val existed = tags.value.any { it.id != tag.id && it.name == name }
            container.tagRepository.renameTag(tag.id, name)
            _events.send(if (existed) "已合并到 #$name" else "已重命名为 #$name")
        }
    }

    fun delete(tag: TagWithCount) {
        viewModelScope.launch {
            container.tagRepository.deleteTag(tag.id)
            _events.send("已删除 #${tag.name}（正文中的 #${tag.name} 已一并移除）")
        }
    }
}

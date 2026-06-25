package com.autodict.ui.list

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.autodict.data.diary.createDiaryRepository
import com.autodict.domain.model.DiaryEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ListUiState(
    val loading: Boolean = true,
    val hasFolder: Boolean = true,
    val entries: List<DiaryEntry> = emptyList(),
)

class EntryListViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = createDiaryRepository(app)

    private val _ui = MutableStateFlow(ListUiState())
    val ui: StateFlow<ListUiState> = _ui.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val hasFolder = repo.hasFolder()
            val entries = if (hasFolder) repo.list() else emptyList()
            _ui.value = ListUiState(loading = false, hasFolder = hasFolder, entries = entries)
        }
    }
}

package com.autodict.ui.list

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.autodict.data.diary.createDiaryRepository
import com.autodict.domain.model.DiaryEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ListUiState(
    val loading: Boolean = true,
    val hasFolder: Boolean = true,
    val entries: List<DiaryEntry> = emptyList(),
)

class EntryListViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = createDiaryRepository(app)

    private data class Meta(val loading: Boolean = true, val hasFolder: Boolean = true)
    private val meta = MutableStateFlow(Meta())

    /**
     * Lista kjem reaktivt frå den lokale indeks-cachen (visast straks), kombinert med
     * laste-/mappe-status. [refresh] utløyser ein billig [com.autodict.data.diary.DiaryRepository.sync].
     */
    val ui: StateFlow<ListUiState> =
        combine(meta, repo.observeEntries()) { m, entries ->
            ListUiState(loading = m.loading, hasFolder = m.hasFolder, entries = entries)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListUiState())

    fun refresh() {
        viewModelScope.launch {
            val hasFolder = repo.hasFolder()
            meta.value = Meta(loading = false, hasFolder = hasFolder)
            if (hasFolder) repo.sync()
        }
    }
}

package com.autodict.ui.home

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

data class HomeListUiState(
    val loading: Boolean = true,
    val hasFolder: Boolean = true,
    val entries: List<DiaryEntry> = emptyList(),
)

/**
 * Oppføringane på heimeskjermen. Lista kjem reaktivt frå den lokale indeks-cachen (visast
 * straks), medan [refresh] gjer ei billig avstemming mot mappa.
 */
class HomeListViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = createDiaryRepository(app)

    private data class Meta(val loading: Boolean = true, val hasFolder: Boolean = true)
    private val meta = MutableStateFlow(Meta())

    val ui: StateFlow<HomeListUiState> =
        combine(meta, repo.observeEntries()) { m, entries ->
            HomeListUiState(loading = m.loading, hasFolder = m.hasFolder, entries = entries)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeListUiState())

    /**
     * Slettar ei oppføring. Lista oppdaterer seg av seg sjølv – ho ser på indeksen, og
     * [com.autodict.data.diary.DiaryRepository.delete] tek oppføringa ut der.
     */
    fun delete(entry: DiaryEntry) {
        viewModelScope.launch { repo.delete(entry) }
    }

    fun refresh() {
        viewModelScope.launch {
            val hasFolder = repo.hasFolder()
            meta.value = Meta(loading = false, hasFolder = hasFolder)
            if (hasFolder) repo.sync()
        }
    }
}

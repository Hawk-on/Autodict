package com.autodict.ui.detail

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.autodict.data.diary.createDiaryRepository
import com.autodict.domain.model.DiaryEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DetailUiState(
    val loading: Boolean = true,
    val entry: DiaryEntry? = null,
    val audioUri: Uri? = null,
)

class EntryDetailViewModel(
    app: Application,
    handle: SavedStateHandle,
) : AndroidViewModel(app) {

    private val repo = createDiaryRepository(app)
    private val entryId: String = handle.get<String>("entryId").orEmpty()

    private val _ui = MutableStateFlow(DetailUiState())
    val ui: StateFlow<DetailUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val loaded = repo.get(entryId)
            _ui.value = DetailUiState(
                loading = false,
                entry = loaded?.entry,
                audioUri = loaded?.audioUri,
            )
        }
    }
}

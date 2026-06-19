package com.vynce.app.viewmodels

import androidx.lifecycle.SavedStateHandle
import com.vynce.app.data.search.SearchRepository
import com.vynce.app.models.UnifiedSearchResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

sealed interface UnifiedSearchUiState {
    object Loading : UnifiedSearchUiState
    data class Success(val result: UnifiedSearchResult) : UnifiedSearchUiState
    data class Error(val message: String) : UnifiedSearchUiState
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class UnifiedSearchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val searchRepository: SearchRepository
) : BaseViewModel() {

    val initialQuery = savedStateHandle.get<String>("query") ?: ""
    private val _query = MutableStateFlow(initialQuery)
    val query = _query.asStateFlow()

    private val _uiState = MutableStateFlow<UnifiedSearchUiState>(UnifiedSearchUiState.Loading)
    val uiState = _uiState.asStateFlow()

    init {
        launchIO {
            _query
                .debounce(500)
                .distinctUntilChanged()
                .flatMapLatest { q ->
                    flow {
                        if (q.isBlank()) {
                            emit(UnifiedSearchUiState.Success(UnifiedSearchResult(q, emptyList(), emptyList(), emptyList(), emptyList(), null)))
                            return@flow
                        }
                        emit(UnifiedSearchUiState.Loading)
                        try {
                            val result = searchRepository.performSearch(q)
                            emit(UnifiedSearchUiState.Success(result))
                        } catch (e: Exception) {
                            android.util.Log.e("UnifiedSearchVM", "Search failed: ${e.message}", e)
                            emit(UnifiedSearchUiState.Error(e.message ?: "Search failed. Please try again."))
                        }
                    }
                }
                .collect {
                    _uiState.value = it
                }
        }
    }

    fun updateQuery(newQuery: String) {
        _query.value = newQuery
    }

    fun retry() {
        val q = _query.value
        if (q.isNotBlank()) {
            launchIO {
                // Toggle query to trigger flatMapLatest again
                _query.value = ""
                _query.value = q
            }
        }
    }
}

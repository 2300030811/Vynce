package com.vynce.app.viewmodels

import androidx.lifecycle.SavedStateHandle
import com.vynce.jiosaavn.JioSaavn
import com.vynce.jiosaavn.SaavnSong
import com.vynce.app.data.search.SearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class OnlineSearchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val searchRepository: SearchRepository,
) : BaseViewModel() {
    val query = savedStateHandle.get<String>("query")!!
    private val _searchResults = MutableStateFlow<List<SaavnSong>>(emptyList())
    val searchResults = _searchResults.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    init {
        search()
    }

    fun search() {
        launchIO {
            _isLoading.value = true
            try {
                val results = searchRepository.searchSongs(query)
                _searchResults.value = results
            } catch (e: Exception) {
                android.util.Log.e("OnlineSearchVM", "Search failed: ${e.message}")
            }
            _isLoading.value = false
        }
    }

    fun loadMore() {
        // Pagination not implemented for JioSaavn yet
    }
}

package com.vynce.app.ui.screens.saavn

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zionhuang.jiosaavn.JioSaavn
import com.zionhuang.jiosaavn.SaavnSong
import com.vynce.app.data.search.SearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SaavnViewModel @Inject constructor(
    private val searchRepository: SearchRepository
) : ViewModel() {

    private val _searchResults = MutableStateFlow<List<SaavnSong>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    private val _charts = MutableStateFlow<List<SaavnSong>>(emptyList())
    val charts = _charts.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    init {
        loadCharts()
    }

    fun search(query: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val results = searchRepository.searchSongs(query)
                Log.d(TAG, "Search results: ${results.size} for '$query'")
                if (results.isEmpty()) {
                    Log.w(TAG, "No results — API may be down")
                }
                _searchResults.value = results
            } catch (e: Exception) {
                Log.e(TAG, "Search failed: ${e.message}")
            }
            _isLoading.value = false
        }
    }

    private fun loadCharts() {
        viewModelScope.launch {
            try {
                _charts.value = JioSaavn.getFeaturedSongs()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load charts: ${e.message}")
            }
        }
    }

    companion object {
        private val TAG = SaavnViewModel::class.simpleName.toString()
    }
}
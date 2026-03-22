package com.vynce.app.ui.screens.saavn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zionhuang.jiosaavn.JioSaavn
import com.zionhuang.jiosaavn.SaavnSong
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SaavnViewModel @Inject constructor() : ViewModel() {

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
                val results = JioSaavn.searchSongs(query)
                android.util.Log.d("SaavnVM", "Search results: ${results.size} for '$query'")
                if (results.isEmpty()) {
                    android.util.Log.w("SaavnVM", "No results — API may be down")
                }
                _searchResults.value = results
            } catch (e: Exception) {
                android.util.Log.e("SaavnVM", "Search failed: ${e.message}")
            }
            _isLoading.value = false
        }
    }

    private fun loadCharts() {
        viewModelScope.launch {
            _charts.value = JioSaavn.getCharts()
        }
    }
}
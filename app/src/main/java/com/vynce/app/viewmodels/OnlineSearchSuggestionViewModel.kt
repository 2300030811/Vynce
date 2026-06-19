package com.vynce.app.viewmodels

import com.vynce.app.data.search.SearchRepository
import com.vynce.app.db.MusicDatabase
import com.vynce.app.db.entities.SearchHistory
import com.zionhuang.jiosaavn.SaavnSong
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class OnlineSearchSuggestionViewModel @Inject constructor(
    database: MusicDatabase,
    private val searchRepository: SearchRepository
) : DatabaseViewModel(database) {
    val query = MutableStateFlow("")
    private val _viewState = MutableStateFlow(SearchSuggestionViewState())
    val viewState = _viewState.asStateFlow()

    init {
        ioScope.launch {
            query
                .debounce(300)
                .distinctUntilChanged()
                .flatMapLatest { queryStr ->
                    if (queryStr.isEmpty()) {
                        database.searchHistory().map { history ->
                            SearchSuggestionViewState(
                                history = history
                            )
                        }
                    } else {
                        // Use SearchRepository for suggestions to coalesce and cache with UnifiedSearchViewModel
                        val searchResult = try {
                            searchRepository.performSearch(queryStr)
                        } catch (e: Exception) {
                            null
                        }
                        
                        val results = searchResult?.songs ?: emptyList()
                        database.searchHistory(queryStr)
                            .map { it.take(3) }
                            .map { history ->
                                SearchSuggestionViewState(
                                    history = history,
                                    suggestions = results.map { it.name }.distinct().take(5),
                                    items = results.take(10)
                                )
                            }
                    }
                }.collect {
                    _viewState.value = it
                }
        }
    }
}

data class SearchSuggestionViewState(
    val history: List<SearchHistory> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val items: List<SaavnSong> = emptyList(),
)


/*
 * Copyright (C) 2025 Vynce Project
 *
 * SPDX-License-Identifier: GPL-3.0
 */

package com.vynce.app.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vynce.app.db.MusicDatabase
import com.vynce.app.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Base ViewModel for Vynce that consolidates common patterns:
 * - Database access
 * - Context access
 * - DataStore access
 * - Coroutine scope helpers
 * - StateFlow creation helpers
 *
 * Extend this class instead of ViewModel() directly to reduce boilerplate.
 */
abstract class BaseViewModel : ViewModel() {

    protected val ioScope = CoroutineScope(Dispatchers.IO + viewModelScope.coroutineContext)

    /**
     * Launch a coroutine in the IO dispatcher.
     */
    protected fun launchIO(block: suspend CoroutineScope.() -> Unit) {
        ioScope.launch { block() }
    }

    /**
     * Convert a Flow to a StateFlow that is scoped to viewModelScope.
     */
    protected fun <T> Flow<T>.asStateFlow(
        initialValue: T,
        started: SharingStarted = SharingStarted.Lazily
    ): StateFlow<T> = stateIn(viewModelScope, started, initialValue)
}

/**
 * Base ViewModel with database access.
 * Use this for ViewModels that need to query the database.
 */
abstract class DatabaseViewModel(
    protected val database: MusicDatabase
) : BaseViewModel()

/**
 * Base ViewModel with database and context access.
 * Use this for ViewModels that need both database and context (for DataStore, resources, etc.).
 */
abstract class ContextDatabaseViewModel(
    @ApplicationContext protected val context: Context,
    protected val database: MusicDatabase
) : BaseViewModel() {
    protected val dataStore get() = context.dataStore
}

/**
 * Base ViewModel with database, context, and saved state handle.
 * Use this for ViewModels that are tied to navigation destinations.
 */
abstract class NavViewModel(
    @ApplicationContext protected val context: Context,
    protected val database: MusicDatabase,
    protected val savedStateHandle: SavedStateHandle
) : BaseViewModel() {
    protected val dataStore get() = context.dataStore
}

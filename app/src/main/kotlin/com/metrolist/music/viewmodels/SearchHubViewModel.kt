/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.pages.MoodAndGenres
import com.metrolist.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchHubViewModel
    @Inject
    constructor() : ViewModel() {
        private val _categories = MutableStateFlow<List<MoodAndGenres.Item>>(emptyList())
        val categories = _categories.asStateFlow()

        private val _loading = MutableStateFlow(true)
        val loading = _loading.asStateFlow()

        init {
            viewModelScope.launch(Dispatchers.IO) {
                load()
            }
        }

        private suspend fun load() {
            _loading.value = true
            YouTube
                .explore()
                .onSuccess { page ->
                    _categories.value =
                        page.moodAndGenres.distinctBy {
                            "${it.title}_${it.endpoint.browseId}_${it.endpoint.params}"
                        }
                }.onFailure {
                    reportException(it)
                    YouTube
                        .moodAndGenres()
                        .onSuccess { sections ->
                            _categories.value =
                                sections
                                    .flatMap { it.items }
                                    .distinctBy { "${it.title}_${it.endpoint.browseId}_${it.endpoint.params}" }
                        }.onFailure(reportException)
                }
            _loading.value = false
        }
    }

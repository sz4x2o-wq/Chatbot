package com.example.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.BookmarkRepository
import com.example.data.SavedBookmark
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

const val SARKARI_HOME_URL = "https://sarkarisahayata.xyz/"

enum class PortalTab {
    PORTAL,
    EXPLORE,
    SAVED
}

class SarkariPortalViewModel(
    private val bookmarkRepository: BookmarkRepository
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(PortalTab.PORTAL)
    val selectedTab: StateFlow<PortalTab> = _selectedTab.asStateFlow()

    private val _currentUrl = MutableStateFlow(SARKARI_HOME_URL)
    val currentUrl: StateFlow<String> = _currentUrl.asStateFlow()

    private val _pageTitle = MutableStateFlow("Sarkari Sahayata")
    val pageTitle: StateFlow<String> = _pageTitle.asStateFlow()

    private val _loadingProgress = MutableStateFlow(0f)
    val loadingProgress: StateFlow<Float> = _loadingProgress.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _canGoBack = MutableStateFlow(false)
    val canGoBack: StateFlow<Boolean> = _canGoBack.asStateFlow()

    private val _pendingUrl = MutableStateFlow<String?>(null)
    val pendingUrl: StateFlow<String?> = _pendingUrl.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _showDisclaimerDialog = MutableStateFlow(false)
    val showDisclaimerDialog: StateFlow<Boolean> = _showDisclaimerDialog.asStateFlow()

    val bookmarks: StateFlow<List<SavedBookmark>> = bookmarkRepository.bookmarksFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val isCurrentPageBookmarked: StateFlow<Boolean> = combine(
        _currentUrl,
        bookmarks
    ) { url, saved ->
        saved.any { it.url == url.trim() }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false
    )

    fun selectTab(tab: PortalTab) {
        _selectedTab.value = tab
    }

    fun updateUrl(url: String?) {
        if (!url.isNullOrBlank()) {
            _currentUrl.value = url
        }
    }

    fun updateTitle(title: String?) {
        if (!title.isNullOrBlank()) {
            _pageTitle.value = title
        }
    }

    fun updateProgress(progress: Int) {
        _loadingProgress.value = progress / 100f
        if (progress >= 100) {
            _isLoading.value = false
        }
    }

    fun updateLoadingState(loading: Boolean) {
        _isLoading.value = loading
        if (!loading) {
            _loadingProgress.value = 1f
        }
    }

    fun updateCanGoBack(canBack: Boolean) {
        _canGoBack.value = canBack
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun openUrlInPortal(url: String) {
        _pendingUrl.value = url
        _selectedTab.value = PortalTab.PORTAL
    }

    fun searchPortal(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            openUrlInPortal(SARKARI_HOME_URL)
            return
        }
        val encoded = Uri.encode(trimmed)
        openUrlInPortal("${SARKARI_HOME_URL}?s=$encoded")
    }

    fun consumePendingUrl() {
        _pendingUrl.value = null
    }

    fun toggleCurrentBookmark(onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val added = bookmarkRepository.toggleBookmark(
                url = _currentUrl.value,
                title = _pageTitle.value
            )
            onResult(added)
        }
    }

    fun removeBookmark(url: String) {
        viewModelScope.launch {
            bookmarkRepository.removeBookmark(url)
        }
    }

    fun setDisclaimerVisible(visible: Boolean) {
        _showDisclaimerDialog.value = visible
    }

    class Factory(private val repository: BookmarkRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SarkariPortalViewModel(repository) as T
        }
    }
}

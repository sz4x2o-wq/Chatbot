package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.BookmarkRepository
import com.example.ui.OfflineScreen
import com.example.ui.SarkariPortalShell
import com.example.ui.SarkariPortalViewModel
import com.example.ui.SavedBookmarksScreen
import com.example.ui.SplashScreen
import com.example.ui.theme.SarkariSahayataTheme
import com.example.util.NetworkMonitor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

enum class AppScreenState {
    SPLASH,
    PORTAL,
    OFFLINE,
    OFFLINE_SAVED
}

class MainActivity : ComponentActivity() {

    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var bookmarkRepository: BookmarkRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        try {
            val wasmCacheDir = File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/wasm")
            if (!wasmCacheDir.exists()) {
                wasmCacheDir.mkdirs()
            }
            val jsCacheDir = File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/js")
            if (!jsCacheDir.exists()) {
                jsCacheDir.mkdirs()
            }
        } catch (_: Exception) {
        }

        networkMonitor = NetworkMonitor(applicationContext)
        bookmarkRepository = BookmarkRepository(applicationContext)

        setContent {
            SarkariSahayataTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SarkariSahayataApp(
                        networkMonitor = networkMonitor,
                        bookmarkRepository = bookmarkRepository
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SarkariSahayataApp(
    networkMonitor: NetworkMonitor,
    bookmarkRepository: BookmarkRepository
) {
    val viewModel: SarkariPortalViewModel = viewModel(
        factory = SarkariPortalViewModel.Factory(bookmarkRepository)
    )
    val isOnline by networkMonitor.isOnline.collectAsStateWithLifecycle(
        initialValue = networkMonitor.checkCurrentConnectivity()
    )
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()

    var currentScreen by remember { mutableStateOf(AppScreenState.SPLASH) }
    var isRetrying by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(isOnline) {
        if (isOnline && currentScreen == AppScreenState.OFFLINE) {
            delay(500)
            currentScreen = AppScreenState.PORTAL
        }
    }

    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "AppScreenTransition"
    ) { screen ->
        when (screen) {
            AppScreenState.SPLASH -> {
                SplashScreen(
                    onSplashFinished = {
                        currentScreen = if (isOnline) {
                            AppScreenState.PORTAL
                        } else {
                            AppScreenState.OFFLINE
                        }
                    }
                )
            }

            AppScreenState.PORTAL -> {
                SarkariPortalShell(
                    viewModel = viewModel,
                    onErrorEncountered = {
                        currentScreen = AppScreenState.OFFLINE
                    }
                )
            }

            AppScreenState.OFFLINE -> {
                OfflineScreen(
                    isRetrying = isRetrying,
                    isOnlineDetected = isOnline,
                    savedCount = bookmarks.size,
                    onRetry = {
                        coroutineScope.launch {
                            isRetrying = true
                            delay(400)
                            isRetrying = false
                            if (networkMonitor.checkCurrentConnectivity()) {
                                currentScreen = AppScreenState.PORTAL
                            }
                        }
                    },
                    onOpenSaved = {
                        currentScreen = AppScreenState.OFFLINE_SAVED
                    }
                )
            }

            AppScreenState.OFFLINE_SAVED -> {
                BackHandler {
                    currentScreen = AppScreenState.OFFLINE
                }
                Scaffold(
                    topBar = {
                        TopAppBar(
                            modifier = Modifier.statusBarsPadding(),
                            title = { Text(stringResource(id = R.string.nav_saved)) },
                            navigationIcon = {
                                IconButton(onClick = { currentScreen = AppScreenState.OFFLINE }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = stringResource(id = R.string.action_back)
                                    )
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        SavedBookmarksScreen(
                            bookmarks = bookmarks,
                            onOpenBookmark = { url ->
                                viewModel.openUrlInPortal(url)
                                currentScreen = if (networkMonitor.checkCurrentConnectivity()) {
                                    AppScreenState.PORTAL
                                } else {
                                    AppScreenState.OFFLINE
                                }
                            },
                            onDeleteBookmark = viewModel::removeBookmark,
                            onBrowsePortal = {
                                currentScreen = if (networkMonitor.checkCurrentConnectivity()) {
                                    AppScreenState.PORTAL
                                } else {
                                    AppScreenState.OFFLINE
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

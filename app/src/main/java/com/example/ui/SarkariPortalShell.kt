package com.example.ui

import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.theme.SarkariEmerald
import com.example.ui.theme.SarkariGold
import com.example.ui.theme.SarkariSaffron

@Composable
fun SarkariPortalShell(
    viewModel: SarkariPortalViewModel,
    onErrorEncountered: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val currentUrl by viewModel.currentUrl.collectAsStateWithLifecycle()
    val pageTitle by viewModel.pageTitle.collectAsStateWithLifecycle()
    val loadingProgress by viewModel.loadingProgress.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val canGoBack by viewModel.canGoBack.collectAsStateWithLifecycle()
    val pendingUrl by viewModel.pendingUrl.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val isBookmarked by viewModel.isCurrentPageBookmarked.collectAsStateWithLifecycle()
    val showDisclaimer by viewModel.showDisclaimerDialog.collectAsStateWithLifecycle()

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }

    BackHandler(enabled = selectedTab != PortalTab.PORTAL) {
        viewModel.selectTab(PortalTab.PORTAL)
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        val contentBlock: @Composable (Modifier) -> Unit = { contentModifier ->
            Column(modifier = contentModifier.fillMaxSize()) {
                PortalTopBar(
                    selectedTab = selectedTab,
                    pageTitle = pageTitle,
                    currentUrl = currentUrl,
                    canGoBack = canGoBack,
                    isBookmarked = isBookmarked,
                    isLoading = isLoading,
                    loadingProgress = loadingProgress,
                    menuExpanded = menuExpanded,
                    onMenuExpandedChange = { menuExpanded = it },
                    onBackClick = { webViewRef?.goBack() },
                    onHomeClick = {
                        viewModel.openUrlInPortal(SARKARI_HOME_URL)
                    },
                    onRefreshClick = {
                        if (selectedTab != PortalTab.PORTAL) {
                            viewModel.selectTab(PortalTab.PORTAL)
                        }
                        webViewRef?.reload()
                    },
                    onToggleBookmark = {
                        viewModel.toggleCurrentBookmark { added ->
                            val msg = if (added) "Saved to Bookmarks" else "Removed from Bookmarks"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onShareClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, pageTitle)
                            putExtra(Intent.EXTRA_TEXT, currentUrl)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share with"))
                    },
                    onOpenInBrowserClick = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl)))
                        } catch (_: Exception) {
                            Toast.makeText(context, "No browser found", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onShowDisclaimerClick = {
                        viewModel.setDisclaimerVisible(true)
                    }
                )

                // Main Portal Viewport (keeps WebView alive across tab switches)
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    SarkariWebView(
                        modifier = Modifier.fillMaxSize(),
                        initialUrl = SARKARI_HOME_URL,
                        pendingUrl = pendingUrl,
                        onPendingUrlConsumed = { viewModel.consumePendingUrl() },
                        canGoBack = canGoBack && selectedTab == PortalTab.PORTAL,
                        onProgressUpdate = viewModel::updateProgress,
                        onLoadingStateChanged = viewModel::updateLoadingState,
                        onTitleReceived = viewModel::updateTitle,
                        onUrlChanged = viewModel::updateUrl,
                        onHistoryChanged = viewModel::updateCanGoBack,
                        onWebViewReady = { wv -> webViewRef = wv },
                        onErrorEncountered = onErrorEncountered
                    )

                    if (selectedTab == PortalTab.EXPLORE) {
                        ExploreScreen(
                            modifier = Modifier.fillMaxSize(),
                            searchQuery = searchQuery,
                            onSearchQueryChange = viewModel::updateSearchQuery,
                            onSearchSubmit = viewModel::searchPortal,
                            onOpenHome = { viewModel.openUrlInPortal(SARKARI_HOME_URL) }
                        )
                    } else if (selectedTab == PortalTab.SAVED) {
                        SavedBookmarksScreen(
                            modifier = Modifier.fillMaxSize(),
                            bookmarks = bookmarks,
                            onOpenBookmark = { url -> viewModel.openUrlInPortal(url) },
                            onDeleteBookmark = viewModel::removeBookmark,
                            onBrowsePortal = { viewModel.selectTab(PortalTab.PORTAL) }
                        )
                    }
                }
            }
        }

        if (useRail) {
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    modifier = Modifier.fillMaxHeight(),
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Spacer(modifier = Modifier.height(12.dp))
                    NavigationRailItem(
                        selected = selectedTab == PortalTab.PORTAL,
                        onClick = { viewModel.selectTab(PortalTab.PORTAL) },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == PortalTab.PORTAL) Icons.Rounded.Public else Icons.Outlined.Public,
                                contentDescription = stringResource(id = R.string.nav_portal)
                            )
                        },
                        label = { Text(stringResource(id = R.string.nav_portal)) },
                        modifier = Modifier.testTag("nav_rail_portal")
                    )
                    NavigationRailItem(
                        selected = selectedTab == PortalTab.EXPLORE,
                        onClick = { viewModel.selectTab(PortalTab.EXPLORE) },
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == PortalTab.EXPLORE) Icons.Rounded.Explore else Icons.Outlined.Explore,
                                contentDescription = stringResource(id = R.string.nav_explore)
                            )
                        },
                        label = { Text(stringResource(id = R.string.nav_explore)) },
                        modifier = Modifier.testTag("nav_rail_explore")
                    )
                    NavigationRailItem(
                        selected = selectedTab == PortalTab.SAVED,
                        onClick = { viewModel.selectTab(PortalTab.SAVED) },
                        icon = {
                            SavedNavIcon(
                                selected = selectedTab == PortalTab.SAVED,
                                count = bookmarks.size
                            )
                        },
                        label = { Text(stringResource(id = R.string.nav_saved)) },
                        modifier = Modifier.testTag("nav_rail_saved")
                    )
                }
                contentBlock(Modifier.weight(1f))
            }
        } else {
            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    NavigationBar(
                        windowInsets = WindowInsets.navigationBars,
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 4.dp
                    ) {
                        NavigationBarItem(
                            selected = selectedTab == PortalTab.PORTAL,
                            onClick = { viewModel.selectTab(PortalTab.PORTAL) },
                            icon = {
                                Icon(
                                    imageVector = if (selectedTab == PortalTab.PORTAL) Icons.Rounded.Public else Icons.Outlined.Public,
                                    contentDescription = stringResource(id = R.string.nav_portal)
                                )
                            },
                            label = { Text(stringResource(id = R.string.nav_portal)) },
                            modifier = Modifier.testTag("nav_bar_portal")
                        )
                        NavigationBarItem(
                            selected = selectedTab == PortalTab.EXPLORE,
                            onClick = { viewModel.selectTab(PortalTab.EXPLORE) },
                            icon = {
                                Icon(
                                    imageVector = if (selectedTab == PortalTab.EXPLORE) Icons.Rounded.Explore else Icons.Outlined.Explore,
                                    contentDescription = stringResource(id = R.string.nav_explore)
                                )
                            },
                            label = { Text(stringResource(id = R.string.nav_explore)) },
                            modifier = Modifier.testTag("nav_bar_explore")
                        )
                        NavigationBarItem(
                            selected = selectedTab == PortalTab.SAVED,
                            onClick = { viewModel.selectTab(PortalTab.SAVED) },
                            icon = {
                                SavedNavIcon(
                                    selected = selectedTab == PortalTab.SAVED,
                                    count = bookmarks.size
                                )
                            },
                            label = { Text(stringResource(id = R.string.nav_saved)) },
                            modifier = Modifier.testTag("nav_bar_saved")
                        )
                    }
                }
            ) { innerPadding ->
                contentBlock(Modifier.padding(innerPadding))
            }
        }
    }

    if (showDisclaimer) {
        AlertDialog(
            onDismissRequest = { viewModel.setDisclaimerVisible(false) },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text(stringResource(id = R.string.disclaimer_title)) },
            text = { Text(stringResource(id = R.string.disclaimer_body)) },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.setDisclaimerVisible(false) },
                    modifier = Modifier.testTag("disclaimer_ok_button")
                ) {
                    Text("Got it")
                }
            }
        )
    }
}

@Composable
private fun SavedNavIcon(selected: Boolean, count: Int) {
    BadgedBox(
        badge = {
            if (count > 0) {
                Badge(containerColor = SarkariSaffron, contentColor = Color.White) {
                    Text(if (count > 99) "99+" else count.toString())
                }
            }
        }
    ) {
        Icon(
            imageVector = if (selected) Icons.Rounded.Bookmarks else Icons.Outlined.Bookmarks,
            contentDescription = stringResource(id = R.string.nav_saved)
        )
    }
}

@Composable
private fun PortalTopBar(
    selectedTab: PortalTab,
    pageTitle: String,
    currentUrl: String,
    canGoBack: Boolean,
    isBookmarked: Boolean,
    isLoading: Boolean,
    loadingProgress: Float,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onBackClick: () -> Unit,
    onHomeClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onToggleBookmark: () -> Unit,
    onShareClick: () -> Unit,
    onOpenInBrowserClick: () -> Unit,
    onShowDisclaimerClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectedTab == PortalTab.PORTAL && canGoBack) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("topbar_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(id = R.string.action_back)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(8.dp))
                    Image(
                        painter = painterResource(id = R.drawable.img_sarkari_logo),
                        contentDescription = stringResource(id = R.string.app_name),
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(9.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = R.string.app_name),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = "HTTPS Secured",
                            tint = SarkariEmerald,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        val subtitleText = when (selectedTab) {
                            PortalTab.PORTAL -> pageTitle.ifBlank { "sarkarisahayata.xyz" }
                            PortalTab.EXPLORE -> "Search & Popular Topics"
                            PortalTab.SAVED -> "Saved Bookmarks"
                        }
                        Text(
                            text = subtitleText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (selectedTab == PortalTab.PORTAL) {
                    IconButton(
                        onClick = onToggleBookmark,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("topbar_bookmark_button")
                    ) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                            contentDescription = stringResource(
                                id = if (isBookmarked) R.string.action_remove_bookmark else R.string.action_bookmark
                            ),
                            tint = if (isBookmarked) SarkariGold else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(
                        onClick = onRefreshClick,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("topbar_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = stringResource(id = R.string.action_refresh)
                        )
                    }
                }

                Box {
                    IconButton(
                        onClick = { onMenuExpandedChange(true) },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("topbar_more_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "More options"
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { onMenuExpandedChange(false) }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(id = R.string.action_home)) },
                            leadingIcon = {
                                Icon(Icons.Rounded.Home, contentDescription = null)
                            },
                            onClick = {
                                onMenuExpandedChange(false)
                                onHomeClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(id = R.string.action_share)) },
                            leadingIcon = {
                                Icon(Icons.Rounded.Share, contentDescription = null)
                            },
                            onClick = {
                                onMenuExpandedChange(false)
                                onShareClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(id = R.string.action_open_browser)) },
                            leadingIcon = {
                                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null)
                            },
                            onClick = {
                                onMenuExpandedChange(false)
                                onOpenInBrowserClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(id = R.string.disclaimer_title)) },
                            leadingIcon = {
                                Icon(Icons.Rounded.Info, contentDescription = null)
                            },
                            onClick = {
                                onMenuExpandedChange(false)
                                onShowDisclaimerClick()
                            }
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = selectedTab == PortalTab.PORTAL && isLoading && loadingProgress < 1.0f,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                LinearProgressIndicator(
                    progress = { loadingProgress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp),
                    color = SarkariSaffron,
                    trackColor = Color.Transparent
                )
            }
        }
    }
}

package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.BookmarkRepository
import com.example.ui.SARKARI_HOME_URL
import com.example.ui.SarkariPortalViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Sarkari Sahayata", appName)
    }

    @Test
    fun `bookmark repository toggles and removes bookmarks`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = BookmarkRepository(context)
        repo.clearAll()

        val added = repo.toggleBookmark("https://sarkarisahayata.xyz/pm-kisan", "PM Kisan Yojana")
        assertTrue(added)

        val saved = repo.bookmarksFlow.first()
        assertEquals(1, saved.size)
        assertEquals("PM Kisan Yojana", saved.first().title)

        val removed = repo.toggleBookmark("https://sarkarisahayata.xyz/pm-kisan", "PM Kisan Yojana")
        assertFalse(removed)
        assertTrue(repo.bookmarksFlow.first().isEmpty())
    }

    @Test
    fun `searchPortal builds sarkarisahayata search URL`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = BookmarkRepository(context)
        val viewModel = SarkariPortalViewModel(repo)

        viewModel.searchPortal("admit card")
        assertEquals("${SARKARI_HOME_URL}?s=admit%20card", viewModel.pendingUrl.value)
    }
}

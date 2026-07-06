package com.rajasudhan.taskmind.data.source

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.CalendarContract
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * The one-way calendar mirror (#119), tested against a small in-memory fake calendar provider —
 * Robolectric ships no CalendarContract provider, so we register our own to observe the real
 * insert/update/delete a device calendar would receive.
 */
@RunWith(RobolectricTestRunner::class)
class CalendarMirrorTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val settings = mockk<SettingsManager>(relaxed = true)
    private val sourceManager = mockk<SourceManager>(relaxed = true)
    private lateinit var provider: FakeCalendarProvider
    private lateinit var mirror: CalendarMirror

    @Before
    fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.WRITE_CALENDAR)
        every { settings.calendarId } returns SettingsManager.CALENDAR_ID_AUTO
        every { settings.eventDurationMinutes } returns 30
        every { sourceManager.isCalendarEnabled } returns flowOf(true)
        provider = Robolectric.buildContentProvider(FakeCalendarProvider::class.java)
            .create(ProviderInfo().apply { authority = CalendarContract.AUTHORITY }).get()
        mirror = CalendarMirror(app, settings, sourceManager)
    }

    @Test
    fun insert_writesTheEvent_andReturnsItsId() = runTest {
        val id = mirror.insert("Standup", "desc", "2026-07-01", "09:00")

        assertEquals(1L, id)
        assertEquals(1, provider.events.size)
        assertEquals("Standup", provider.events[1L]?.getAsString(CalendarContract.Events.TITLE))
    }

    @Test
    fun insert_returnsNull_whenTheCalendarSourceIsOff() = runTest {
        every { sourceManager.isCalendarEnabled } returns flowOf(false)

        assertNull(mirror.insert("Standup", null, "2026-07-01", "09:00"))
        assertTrue(provider.events.isEmpty())
    }

    @Test
    fun insert_returnsNull_withoutWriteCalendarPermission() = runTest {
        shadowOf(app).denyPermissions(Manifest.permission.WRITE_CALENDAR)

        assertNull(mirror.insert("Standup", null, "2026-07-01", "09:00"))
        assertTrue(provider.events.isEmpty())
    }

    @Test
    fun update_rewritesTheEventsTitleAndTime() = runTest {
        val id = mirror.insert("Standup", null, "2026-07-01", "09:00")!!

        mirror.update(id, "Standup moved", "2026-07-02", "10:00")

        assertEquals("Standup moved", provider.events[id]?.getAsString(CalendarContract.Events.TITLE))
    }

    @Test
    fun delete_removesTheEvent() = runTest {
        val id = mirror.insert("Standup", null, "2026-07-01", "09:00")!!

        mirror.delete(id)

        assertNull(provider.events[id])
    }
}

/** In-memory stand-in for the device calendar provider: records inserts, applies updates/deletes. */
class FakeCalendarProvider : ContentProvider() {
    val events = linkedMapOf<Long, ContentValues>()
    private var seq = 0L

    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, args: Array<String>?, sort: String?): Cursor {
        return if (uri.toString().contains("calendars")) {
            // One writable primary calendar, so getWritableCalendarId() resolves an id.
            MatrixCursor(arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.IS_PRIMARY, CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL))
                .apply { addRow(arrayOf<Any>(1L, 1, CalendarContract.Calendars.CAL_ACCESS_OWNER)) }
        } else {
            // Dedup query over events: nothing pre-exists.
            MatrixCursor(arrayOf(CalendarContract.Events._ID))
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri {
        val id = ++seq
        events[id] = ContentValues(values)
        return ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<String>?): Int {
        val existing = events[ContentUris.parseId(uri)] ?: return 0
        values?.let { existing.putAll(it) }
        return 1
    }

    override fun delete(uri: Uri, selection: String?, args: Array<String>?): Int =
        if (events.remove(ContentUris.parseId(uri)) != null) 1 else 0

    override fun getType(uri: Uri): String? = null
}

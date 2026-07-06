package com.rajasudhan.taskmind.testutil

import com.rajasudhan.taskmind.data.model.Note
import com.rajasudhan.taskmind.data.model.Suggestion

/**
 * Terse builders for test data. Every field has a sensible default so a test only states what it
 * cares about. Shared across DAO, integration, and (later) ViewModel tests.
 */
fun aNote(
    id: Int = 0,
    title: String = "Title",
    summary: String = "",
    body: String = "Body",
    dueDate: String? = null,
    dueTime: String? = null,
    source: String = "Manual entry",
    createdDate: Long = 1_000L,
    type: String = "note",
    completed: Boolean = false,
    completedDate: Long? = null,
    recurrence: String? = null,
    checklist: String? = null,
    locationLat: Double? = null,
    locationLng: Double? = null,
    locationRadius: Double? = null,
    locationLabel: String? = null,
    priority: String = "normal",
    nag: Boolean = false,
    counterparty: String? = null,
    pendingConfirmSince: Long? = null,
    recurrenceAnchorDay: Int? = null,
    nagFiring: Boolean = false,
    tags: String? = null,
) = Note(
    id = id, title = title, summary = summary, body = body, dueDate = dueDate, dueTime = dueTime,
    source = source, createdDate = createdDate, type = type, completed = completed,
    completedDate = completedDate, recurrence = recurrence, checklist = checklist,
    locationLat = locationLat, locationLng = locationLng, locationRadius = locationRadius,
    locationLabel = locationLabel, priority = priority, nag = nag, counterparty = counterparty,
    pendingConfirmSince = pendingConfirmSince, recurrenceAnchorDay = recurrenceAnchorDay, nagFiring = nagFiring,
    tags = tags,
)

fun aSuggestion(
    id: Int = 0,
    source: String = "SMS from +10000000000",
    rawSnippet: String = "raw snippet",
    extractedTitle: String = "Title",
    summary: String = "",
    dueDate: String? = null,
    dueTime: String? = null,
    type: String = "note",
    confidence: Double = 0.9,
    status: String = "pending",
    snoozedUntil: Long? = null,
    location: String? = null,
    recurrence: String? = null,
    priority: String = "normal",
    counterparty: String? = null,
    tags: String? = null,
) = Suggestion(
    id = id, source = source, rawSnippet = rawSnippet, extractedTitle = extractedTitle,
    summary = summary, dueDate = dueDate, dueTime = dueTime, type = type, confidence = confidence,
    status = status, snoozedUntil = snoozedUntil, location = location, recurrence = recurrence,
    priority = priority, counterparty = counterparty, tags = tags,
)

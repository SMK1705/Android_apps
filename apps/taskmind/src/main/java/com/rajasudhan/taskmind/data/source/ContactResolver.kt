package com.rajasudhan.taskmind.data.source

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Looks up a phone number for a person name in the device contacts. Used so the Call button works
 * for messages that name someone but never include a number (e.g. a WhatsApp "call me", whose
 * notification carries only the sender's contact name). Requires READ_CONTACTS.
 */
object ContactResolver {
    suspend fun lookupNumber(context: Context, name: String): String? {
        val term = name.trim()
        if (term.isBlank()) return null
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) return null
        return withContext(Dispatchers.IO) {
            // Match most-specific first so "Sam" prefers an exact "Sam" over "Samantha"/"Samuel", only
            // falling back to a looser match when there's no better one. LIKE (not =) keeps every tier
            // case-insensitive, matching the old behaviour; the wildcards are what narrow each tier.
            val esc = term.escapeLike()
            queryNumber(context, esc) // exact (case-insensitive)
                ?: queryNumber(context, "$esc%") // prefix
                ?: queryNumber(context, "%$esc%") // substring (original loose behaviour)
        }
    }

    /** First contact number whose display name matches [likePattern]; null on no match/permission/error. */
    private fun queryNumber(context: Context, likePattern: String): String? = runCatching {
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? ESCAPE '\\'",
            arrayOf(likePattern),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0)?.let(PhoneUtil::normalize) else null
        }
    }.getOrNull()

    /** Escapes LIKE metacharacters so a name containing % or _ matches literally (ESCAPE '\'). */
    private fun String.escapeLike(): String =
        replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}

/**
 * The number the Call button should dial for an item: a number stated in its text, or — for a
 * call-intent item with only a name — the matching contact's number. Null if nothing dialable
 * (the button is then hidden, so it never offers a call it can't place).
 */
suspend fun resolveCallNumber(
    context: Context,
    title: String?,
    summary: String?,
    rawSnippet: String?,
    source: String?
): String? {
    val direct = PhoneUtil.extractFirst(title)
        ?: PhoneUtil.extractFirst(summary)
        ?: PhoneUtil.extractFirst(rawSnippet)
        ?: PhoneUtil.extractFirst(source)
    if (direct != null) return direct

    if (!PhoneUtil.isCallIntent(title, summary, rawSnippet)) return null
    val name = PhoneUtil.personName(source, title) ?: return null
    if (!contactsLookupEnabled(context)) return null
    return ContactResolver.lookupNumber(context, name)
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface ContactsSettingEntryPoint {
    fun sourceManager(): SourceManager
}

/** Whether the user allows resolving a name to a number via Contacts (the Sources -> Contacts toggle). */
private suspend fun contactsLookupEnabled(context: Context): Boolean = runCatching {
    EntryPointAccessors
        .fromApplication(context.applicationContext, ContactsSettingEntryPoint::class.java)
        .sourceManager().isContactsEnabled.first()
}.getOrDefault(true)

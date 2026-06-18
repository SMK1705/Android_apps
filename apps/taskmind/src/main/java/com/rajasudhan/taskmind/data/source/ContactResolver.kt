package com.rajasudhan.taskmind.data.source

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Looks up a phone number for a person name in the device contacts. Used so the Call button works
 * for messages that name someone but never include a number (e.g. a WhatsApp "call me", whose
 * notification carries only the sender's contact name). Requires READ_CONTACTS.
 */
object ContactResolver {
    suspend fun lookupNumber(context: Context, name: String): String? {
        if (name.isBlank()) return null
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                    "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                    arrayOf("%${name.trim()}%"),
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0)?.let(PhoneUtil::normalize) else null
                }
            }.getOrNull()
        }
    }
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
    return ContactResolver.lookupNumber(context, name)
}

package com.skrypko.dicefamiliar.ui.common

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns

/** The file name the user sees for a picked document (`OpenableColumns.DISPLAY_NAME`), or `null`. */
fun displayName(resolver: ContentResolver, uri: Uri): String? = try {
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
} catch (_: SecurityException) {
    null
}

package com.example.gbsw.shared.model

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

class AndroidCalendarRepository(private val context: Context) {
    fun hasPermission() = context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    suspend fun nextEvent(nowMillis: Long = System.currentTimeMillis()): CalendarEvent? = withContext(Dispatchers.IO) {
        check(hasPermission()) { "캘린더 권한이 필요해요" }
        val today = Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        val end = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, nowMillis)
            ContentUris.appendId(it, end)
        }.build()
        val projection = arrayOf(CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN, CalendarContract.Instances.EVENT_LOCATION, CalendarContract.Instances.STATUS)
        context.contentResolver.query(uri, projection, null, null, CalendarContract.Instances.BEGIN + " ASC")?.use { cursor ->
            var count = 0
            while (cursor.moveToNext() && count++ < 1000) {
                val id = cursor.getLong(0)
                val title = cursor.getString(1).orEmpty().ifBlank { "제목 없는 일정" }
                val begin = cursor.getLong(2)
                val location = cursor.getString(3).orEmpty().trim()
                val cancelled = cursor.getInt(4) == CalendarContract.Events.STATUS_CANCELED
                if (CalendarSelection.isUpcoming(begin, location, cancelled, nowMillis, end)) {
                    return@withContext CalendarEvent(id, title, begin, location)
                }
            }
        } ?: error("기기에서 캘린더를 읽을 수 없어요")
        null
    }
}

/** Android 제공자와 분리해 일정 선택의 경계 조건을 검증한다. */
object CalendarSelection {
    fun isUpcoming(begin: Long, location: String, cancelled: Boolean, now: Long, endOfDay: Long) =
        begin >= now && begin < endOfDay && location.isNotBlank() && !cancelled
}

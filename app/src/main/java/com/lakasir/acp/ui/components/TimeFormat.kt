package com.lakasir.acp.ui.components

import android.text.format.DateUtils

fun relativeTime(timestamp: Long, now: Long = System.currentTimeMillis()): String =
    if (now - timestamp < DateUtils.MINUTE_IN_MILLIS) {
        "just now"
    } else {
        DateUtils.getRelativeTimeSpanString(timestamp, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()
    }

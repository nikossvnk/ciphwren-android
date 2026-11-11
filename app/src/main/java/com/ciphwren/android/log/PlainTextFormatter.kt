/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.ciphwren.android.log

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.logging.Formatter
import java.util.logging.LogRecord

class PlainTextFormatter private constructor(
        private val logcat: Boolean
): Formatter() {

    companion object {
        val LOGCAT = PlainTextFormatter(true)
        val DEFAULT = PlainTextFormatter(false)

        const val MAX_MESSAGE_LENGTH = 20000

        private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())
    }

    override fun format(r: LogRecord): String {
        val builder = StringBuilder()

        if (!logcat)
            builder .append(dateFormat.format(Instant.ofEpochMilli(r.millis)))
                    .append(" ").append(r.threadID).append(" ")

        val className = shortClassName(r.sourceClassName)
        if (className != r.loggerName)
            builder.append("[").append(className).append("] ")

        builder.append(abbreviate(r.message, MAX_MESSAGE_LENGTH))

        r.thrown?.let {
            builder .append("\nEXCEPTION ")
                    .append(it.stackTraceToString())
        }

        r.parameters?.let {
            for ((idx, param) in it.withIndex())
                builder.append("\n\tPARAMETER #").append(idx).append(" = ").append(param)
        }

        if (!logcat)
            builder.append("\n")

        return builder.toString()
    }

    private fun abbreviate(message: String?, maxLength: Int): String? =
            if (message != null && message.length > maxLength)
                message.take(maxLength - 3) + "..."
            else
                message

    private fun shortClassName(className: String?) = className
            ?.replace(Regex("^at\\.bitfire\\.(dav|cert4an|dav4an|ical4an|vcard4an)droid\\."), "")
            ?.replace(Regex("\\$.*$"), "")

}

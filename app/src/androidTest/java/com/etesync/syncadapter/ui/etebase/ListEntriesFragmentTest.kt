/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.etesync.syncadapter.ui.etebase

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ListEntriesFragmentTest {

    @Test
    fun getLineStripsCarriageReturn() {
        val ics = "BEGIN:VCALENDAR\r\nBEGIN:VTODO\r\nSUMMARY:Buy milk\r\nEND:VTODO\r\nEND:VCALENDAR\r\n"
        assertEquals("Buy milk", ListEntriesFragment.getLine(ics, "SUMMARY:"))
    }

    @Test
    fun getLineReadsLastLineWithoutNewline() {
        assertEquals("Jane Doe", ListEntriesFragment.getLine("BEGIN:VCARD\nFN:Jane Doe", "FN:"))
    }

    @Test
    fun getLineMissingPrefix() {
        assertNull(ListEntriesFragment.getLine("BEGIN:VCARD\r\n", "FN:"))
        assertNull(ListEntriesFragment.getLine(null, "FN:"))
    }

}

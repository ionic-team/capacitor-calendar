package com.capacitorjs.plugins.calendar

import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarErrorTests {

    @Test
    fun errorCodesFollowUnifiedFormat() {
        assertEquals("OS-PLUG-CLDR-0000", CalendarError.UNKNOWN.code)
        assertEquals("OS-PLUG-CLDR-0001", CalendarError.INVALID_ARGUMENT.code)
        assertEquals("OS-PLUG-CLDR-0003", CalendarError.PENDING_OPERATION.code)
        assertEquals("OS-PLUG-CLDR-0004", CalendarError.IO_ERROR.code)
        assertEquals("OS-PLUG-CLDR-0005", CalendarError.NOT_SUPPORTED.code)
        assertEquals("OS-PLUG-CLDR-0006", CalendarError.OPERATION_CANCELLED.code)
        assertEquals("OS-PLUG-CLDR-0020", CalendarError.PERMISSION_DENIED.code)
    }

    @Test
    fun exceptionCarriesTheError() {
        val exception = CalendarException(CalendarError.PERMISSION_DENIED)
        assertEquals(CalendarError.PERMISSION_DENIED, exception.error)
        assertEquals(CalendarError.PERMISSION_DENIED.message, exception.message)
    }
}

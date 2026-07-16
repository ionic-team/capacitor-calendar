package com.capacitorjs.plugins.calendar

import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.annotation.CapacitorPlugin
import com.getcapacitor.PluginMethod

@CapacitorPlugin(name = "Calendar")
class CalendarPlugin : Plugin() {

    private val implementation = Calendar()

    @PluginMethod
    fun echo(call: PluginCall) {
        val value = call.getString("value") ?: ""

        val ret = JSObject().apply {
            put("value", implementation.echo(value))
        }
        call.resolve(ret)
    }
}

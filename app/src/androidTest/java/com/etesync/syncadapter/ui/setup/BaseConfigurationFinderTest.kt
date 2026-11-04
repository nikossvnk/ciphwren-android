/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.etesync.syncadapter.ui.setup

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaseConfigurationFinderTest {

    private val server = MockWebServer()

    @Before
    fun startServer() {
        // a server which doesn't know the Etebase API (like an EteSync 1.0 server)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse.Builder().code(404).build()
        }
        server.start()
    }

    @After
    fun stopServer() {
        server.close()
    }

    @Test
    fun testLoginToNonEtebaseServerFails() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val credentials = LoginCredentials(server.url("/").toUri(), "user", "password")

        val config = BaseConfigurationFinder(context, credentials).findInitialConfiguration()

        assertTrue(config.isFailed)
        assertTrue(config.error is BaseConfigurationFinder.NotEtebaseServerException)
        assertNull(config.etebaseSession)
    }

}

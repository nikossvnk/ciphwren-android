/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package com.etesync.syncadapter.ui.setup

import android.content.Context
import com.etebase.client.Account
import com.etebase.client.EtebaseException
import com.etebase.client.createClient
import com.etebase.client.isEtebaseServer
import com.etesync.syncadapter.Constants
import com.etesync.syncadapter.HttpClient
import com.etesync.syncadapter.R
import com.etesync.syncadapter.log.Logger
import okhttp3.OkHttpClient
import java.io.Serializable
import java.net.URI

class BaseConfigurationFinder(protected val context: Context, protected val credentials: LoginCredentials) {
    protected var httpClient: OkHttpClient = HttpClient.Builder(context).build().okHttpClient

    fun findInitialConfiguration(): Configuration {
        val uri = credentials.uri ?: URI(Constants.etebaseServiceUrl)

        var etebaseSession: String? = null
        var exception: Throwable? = null
        try {
            val client = createClient(httpClient, uri.toString())
            if (credentials.uri != null) {
                // a custom server must be an Etebase server (EteSync 1.0 servers aren't supported anymore)
                val isEtebase = try {
                    isEtebaseServer(client)
                } catch (e: EtebaseException) {
                    throw NotEtebaseServerException(context.getString(R.string.login_not_etebase_server), e)
                }
                if (!isEtebase) {
                    throw NotEtebaseServerException(context.getString(R.string.login_not_etebase_server))
                }
            }

            Logger.log.fine("Attempting to login to etebase")
            val etebase = Account.login(client, credentials.userName, credentials.password)
            etebaseSession = etebase.save(null)
        } catch (e: Exception) {
            Logger.log.warning(e.localizedMessage)
            exception = e
        }

        return Configuration(
                uri,
                credentials.userName,
                etebaseSession,
                exception
        )
    }

    class NotEtebaseServerException(message: String, cause: Throwable? = null): Exception(message, cause)

    // data classes

    class Configuration
    // We have to use URI here because HttpUrl is not serializable!

    (val url: URI?, val userName: String, val etebaseSession: String?, var error: Throwable?) : Serializable {
        val isFailed: Boolean
            get() = this.error != null

        override fun toString(): String {
            return "BaseConfigurationFinder.Configuration(url=" + this.url + ", userName=" + this.userName + ", error=" + this.error + ", failed=" + this.isFailed + ")"
        }
    }

}

package com.etesync.syncadapter

import android.content.Context
import org.acra.config.CoreConfigurationBuilder
import org.acra.config.DialogConfigurationBuilder
import org.acra.config.HttpSenderConfigurationBuilder
import org.acra.data.StringFormat
import org.acra.sender.HttpSender

object AcraConfiguration {
    fun getConfig(context: Context): CoreConfigurationBuilder =
            CoreConfigurationBuilder()
                    .withBuildConfigClass(BuildConfig::class.java)
                    .withLogcatArguments(listOf("-t", "500", "-v", "time"))
                    .withReportFormat(StringFormat.JSON)
                    .withPluginConfigurations(
                            HttpSenderConfigurationBuilder()
                                    .withUri(Constants.crashReportingUrl)
                                    .withHttpMethod(HttpSender.Method.POST)
                                    .build(),
                            DialogConfigurationBuilder()
                                    .withTitle(context.getString(R.string.crash_title))
                                    .withText(context.getString(R.string.crash_message))
                                    .withCommentPrompt(context.getString(R.string.crash_email_body))
                                    .build()
                    )
}

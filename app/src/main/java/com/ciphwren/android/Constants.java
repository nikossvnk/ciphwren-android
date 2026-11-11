/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */
package com.ciphwren.android;

import android.net.Uri;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Constants {

    // notification IDs
    public final static int
            NOTIFICATION_ACCOUNT_SETTINGS_UPDATED = 0,
            NOTIFICATION_EXTERNAL_FILE_LOGGING = 1,
            NOTIFICATION_REFRESH_COLLECTIONS = 2,
            NOTIFICATION_CONTACTS_SYNC = 10,
            NOTIFICATION_CALENDAR_SYNC = 11,
            NOTIFICATION_TASK_SYNC = 12,
            NOTIFICATION_ACCOUNT_UPDATE = 13,
            NOTIFICATION_PERMISSIONS = 20,
            NOTIFICATION_ETESYNC1_UNSUPPORTED = 21;

    public static final Uri sourceCodeUri = Uri.parse("https://github.com/nikossvnk/ciphwren-android");
    public static final Uri reportIssueUri = sourceCodeUri.buildUpon().appendEncodedPath("issues").build();
    public static final String PRODID_BASE = "-//Ciphwren//" + BuildConfig.APPLICATION_ID + "/" + BuildConfig.VERSION_NAME;

    public static final int DEFAULT_SYNC_INTERVAL = 4 * 3600;  // 4 hours
    public static final long DEFAULT_RETRY_DELAY = 30 * 60; // 30 minutes

    public final static String KEY_ACCOUNT = "account";

    public final static String ETEBASE_TYPE_ADDRESS_BOOK = "etebase.vcard";
    public final static String ETEBASE_TYPE_CALENDAR = "etebase.vevent";
    public final static String ETEBASE_TYPE_TASKS = "etebase.vtodo";
    public final static List<String> COLLECTION_TYPES = Collections.unmodifiableList(Arrays.asList(
      ETEBASE_TYPE_ADDRESS_BOOK,
      ETEBASE_TYPE_CALENDAR,
      ETEBASE_TYPE_TASKS
    ));
}

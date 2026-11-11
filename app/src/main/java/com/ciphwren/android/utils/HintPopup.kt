package com.ciphwren.android.utils

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.TextView
import androidx.annotation.StringRes
import com.ciphwren.android.R
import kotlin.math.min

/**
 * Shows a one-time onboarding hint ("Did you know?") in a popup next to [View]s the user should
 * notice. The hint is dismissed by tapping it or anywhere outside of it.
 */
object HintPopup {
    @SuppressLint("InflateParams") // a popup window has no parent view
    fun show(anchor: View, @StringRes description: Int) {
        // the anchor has to be laid out to position the popup next to it
        anchor.post {
            if (!anchor.isAttachedToWindow)
                return@post

            val context = anchor.context
            val content = LayoutInflater.from(context).inflate(R.layout.hint_popup, null)
            content.findViewById<TextView>(R.id.hint_description).setText(description)

            val res = context.resources
            val margin = res.getDimensionPixelSize(R.dimen.hint_popup_margin)
            val width = min(res.displayMetrics.widthPixels - 2 * margin, res.getDimensionPixelSize(R.dimen.hint_popup_max_width))

            val popup = PopupWindow(content, width, ViewGroup.LayoutParams.WRAP_CONTENT, true)
            popup.elevation = res.getDimension(R.dimen.hint_popup_elevation)
            content.setOnClickListener { popup.dismiss() }
            popup.showAsDropDown(anchor)
        }
    }
}

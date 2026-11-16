package com.ciphwren.android.utils

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.widget.PopupWindow
import android.widget.TextView
import androidx.annotation.StringRes
import com.ciphwren.android.R
import kotlin.math.max
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

            // With WRAP_CONTENT the popup doesn't know its height when it's placed, so next to an
            // anchor at the bottom (the add button) it was squeezed under it instead of going above
            content.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            val popup = PopupWindow(content, width, content.measuredHeight, true)
            popup.elevation = res.getDimension(R.dimen.hint_popup_elevation)
            content.setOnClickListener { popup.dismiss() }
            // Keep the margin to the right edge of the screen too (the add button is next to it)
            val anchorX = IntArray(2).also { anchor.getLocationOnScreen(it) }[0]
            val overflow = anchorX + width - (res.displayMetrics.widthPixels - margin)
            popup.showAsDropDown(anchor, -max(overflow, 0), 0)
        }
    }
}

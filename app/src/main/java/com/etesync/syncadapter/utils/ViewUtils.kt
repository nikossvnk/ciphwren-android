package com.etesync.syncadapter.utils

import android.transition.TransitionManager
import android.view.View
import android.view.ViewGroup

/**
 * Shows ([expanded] = true) or hides the view, animating the change of the surrounding layout.
 */
fun View.setExpanded(expanded: Boolean) {
    (parent as? ViewGroup)?.let { parent ->
        TransitionManager.beginDelayedTransition(parent)
    }
    visibility = if (expanded) View.VISIBLE else View.GONE
}

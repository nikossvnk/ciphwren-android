package com.etesync.syncadapter.ui

import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import com.etesync.syncadapter.R
import kotlin.math.roundToInt
import java.util.Locale

/**
 * Platform color controls preserve arbitrary RGB values and opacity without an external picker.
 */
object ColorPickerDialog {
    fun show(context: Context, currentColor: Int, onColorPicked: (Int) -> Unit) {
        val density = context.resources.displayMetrics.density
        val padding = (20 * density).roundToInt()
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }
        val preview = View(context)
        content.addView(preview, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (48 * density).roundToInt()))
        val value = TextView(context)
        content.addView(value)

        fun channel(@StringRes label: Int, initial: Int): SeekBar {
            content.addView(TextView(context).apply { setText(label) })
            return SeekBar(context).apply {
                max = 255
                progress = initial
                contentDescription = context.getString(label)
                content.addView(this, LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            }
        }

        val red = channel(R.string.color_picker_red, Color.red(currentColor))
        val green = channel(R.string.color_picker_green, Color.green(currentColor))
        val blue = channel(R.string.color_picker_blue, Color.blue(currentColor))
        val opacity = channel(R.string.color_picker_opacity, Color.alpha(currentColor))

        fun selectedColor() = Color.argb(opacity.progress, red.progress, green.progress, blue.progress)
        fun updatePreview() {
            val color = selectedColor()
            preview.setBackgroundColor(color)
            val hex = String.format(Locale.ROOT, "#%08X", color)
            preview.contentDescription = hex
            value.text = hex
        }
        val listener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) = updatePreview()
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        }
        red.setOnSeekBarChangeListener(listener)
        green.setOnSeekBarChangeListener(listener)
        blue.setOnSeekBarChangeListener(listener)
        opacity.setOnSeekBarChangeListener(listener)
        updatePreview()

        AlertDialog.Builder(context)
                .setTitle(R.string.choose_color)
                .setView(ScrollView(context).apply { addView(content) })
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok) { _, _ -> onColorPicked(selectedColor()) }
                .show()
    }
}

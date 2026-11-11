package com.ciphwren.android.ui.importlocal

import android.app.Application
import android.content.Context
import android.widget.ProgressBar
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ciphwren.android.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class ImportState(
    val running: Boolean = false,
    val total: Int? = null,
    val progress: Int = 0,
    val result: ResultFragment.ImportResult? = null
)

/** Requests contain only import identifiers, never a fragment, activity, or view. */
internal interface ImportRequest {
    suspend fun load(context: Context, progress: ImportViewModel): ResultFragment.ImportResult
}

internal class ImportViewModel(application: Application) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow(ImportState())
    val state = mutableState.asStateFlow()
    var chooserLaunched = false

    fun start(request: ImportRequest) {
        if (state.value.running || state.value.result != null) return
        mutableState.value = ImportState(running = true)
        viewModelScope.launch {
            val result = try {
                withContext(Dispatchers.IO) {
                    request.load(getApplication<Application>(), this@ImportViewModel)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ResultFragment.ImportResult().also { it.e = e }
            }
            complete(result)
        }
    }

    fun setTotal(total: Int) {
        mutableState.value = state.value.copy(total = total)
    }

    fun entryProcessed() {
        mutableState.value = state.value.copy(progress = state.value.progress + 1)
    }

    fun complete(result: ResultFragment.ImportResult) {
        mutableState.value = state.value.copy(running = false, result = result)
    }

    fun resultDelivered() {
        mutableState.value = ImportState()
    }
}

/** The view owns this dialog; the ViewModel owns only its progress values. */
internal class ImportProgressDialog(context: Context) {
    private val progress = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal)
    val dialog: AlertDialog = AlertDialog.Builder(context)
        .setTitle(R.string.import_dialog_title)
        .setIcon(R.drawable.ic_import_export_black)
        .setMessage(R.string.import_dialog_loading_file)
        .setView(progress)
        .setCancelable(false)
        .create()

    fun render(state: ImportState) {
        dialog.setMessage(dialog.context.getString(if (state.total == null)
            R.string.import_dialog_loading_file else R.string.import_dialog_adding_entries))
        progress.isIndeterminate = state.total == null
        progress.max = state.total ?: 0
        progress.progress = state.progress
    }
}

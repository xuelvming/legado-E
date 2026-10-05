package io.legado.app.help

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.utils.toastOnUi

@RequiresApi(Build.VERSION_CODES.M)
object ProcessTextHelp {

    private fun queryIntent() = Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain")

    fun getSupportedActivities(context: Context): List<ResolveInfo> =
        context.packageManager.queryIntentActivities(queryIntent(), 0)

    fun component(info: ResolveInfo) =
        ComponentName(info.activityInfo.packageName, info.activityInfo.name)

    fun isSelectionApp(context: Context, info: ResolveInfo): Boolean {
        val activity = info.activityInfo
        return activity.packageName != context.packageName &&
            activity.exported && activity.enabled && activity.applicationInfo.enabled &&
            (activity.permission.isNullOrEmpty() ||
                context.checkSelfPermission(activity.permission) == PackageManager.PERMISSION_GRANTED)
    }

    fun getSelectionApps(context: Context): List<ResolveInfo> =
        getSupportedActivities(context)
            .filter { isSelectionApp(context, it) }
            .distinctBy { component(it) }
            .sortedWith(compareBy<ResolveInfo> {
                it.loadLabel(context.packageManager).toString()
            }.thenBy { component(it).flattenToString() })

    fun createIntent(
        component: ComponentName,
        text: String? = null,
        readOnly: Boolean = false
    ): Intent = queryIntent()
        .setComponent(component)
        .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, readOnly)
        .apply {
            if (text != null) putExtra(Intent.EXTRA_PROCESS_TEXT, text)
        }

    fun launch(context: Context, target: String, text: String): Boolean {
        if (text.isBlank()) {
            return reportFailure(context, R.string.selection_app_empty_text, target)
        }
        val targetComponent = ComponentName.unflattenFromString(target)
            ?: return reportFailure(context, R.string.selection_app_unavailable, target)
        try {
            if (getSelectionApps(context).none { component(it) == targetComponent }) {
                return reportFailure(context, R.string.selection_app_unavailable, target)
            }
            context.startActivity(createIntent(targetComponent, text, readOnly = true))
            return true
        } catch (e: ActivityNotFoundException) {
            return reportFailure(context, R.string.selection_app_launch_failed, target, e)
        } catch (e: SecurityException) {
            return reportFailure(context, R.string.selection_app_launch_failed, target, e)
        }
    }

    private fun reportFailure(
        context: Context,
        @StringRes message: Int,
        target: String,
        error: Exception? = null
    ): Boolean {
        context.toastOnUi(message)
        AppLog.put("${context.getString(message)} ($target)", error)
        return false
    }
}

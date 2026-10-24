package com.xtt.toolbox.ui.reading

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.xtt.toolbox.domain.content.ExternalLinkPolicy

/** 外部链接只交给系统处理；本应用不内置浏览器、不发起网络请求。 */
object ExternalLinkLauncher {
    fun open(context: Context, rawUrl: String): Boolean {
        val safeUrl = ExternalLinkPolicy.validate(rawUrl) ?: return false
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl)).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
            }
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }
}

package com.xtt.toolbox.data.profile

import android.graphics.Bitmap
import android.graphics.BitmapFactory

sealed interface AvatarImageState {
    data object None : AvatarImageState
    data object Loading : AvatarImageState
    data class Ready(val bitmap: Bitmap) : AvatarImageState
    data object Failed : AvatarImageState
}

object AvatarLoader {
    /** 头像已经是 512×512 的小文件，只做一次共享解码，避免页面切换重复 IO。 */
    fun load(path: String): Bitmap? = runCatching {
        BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        )
    }.getOrNull()
}

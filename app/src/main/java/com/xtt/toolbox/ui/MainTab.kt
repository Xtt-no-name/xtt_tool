package com.xtt.toolbox.ui

import androidx.annotation.StringRes
import com.xtt.toolbox.R

/** 底部导航的三个主入口。个人中心不占用底部导航位置。 */
enum class MainTab(
    @param:StringRes val labelRes: Int,
    @param:StringRes val subtitleRes: Int,
) {
    READING(
        labelRes = R.string.nav_reading,
        subtitleRes = R.string.reading_subtitle,
    ),
    WRITING(
        labelRes = R.string.nav_writing,
        subtitleRes = R.string.writing_subtitle,
    ),
    TOOLBOX(
        labelRes = R.string.nav_toolbox,
        subtitleRes = R.string.toolbox_subtitle,
    ),
}

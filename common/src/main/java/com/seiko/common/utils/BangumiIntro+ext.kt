package com.seiko.common.utils

import com.seiko.core.model.api.BangumiIntro

fun BangumiIntro.getBangumiStatus(): String {
    if (!isOnAir) return "已完结"
    val onAirDay = when(airDay) {
        0 -> "每周日更新"
        1 -> "每周一更新"
        2 -> "每周二更新"
        3 -> "每周三更新"
        4 -> "每周四更新"
        5 -> "每周五更新"
        6 -> "每周六更新"
        else -> "更新时间未知"
    }
    return "连载中 · $onAirDay"
}
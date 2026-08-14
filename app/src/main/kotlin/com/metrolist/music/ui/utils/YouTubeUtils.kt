/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.utils

fun String.resize(
    width: Int? = null,
    height: Int? = null,
): String {
    if (width == null && height == null) return this

    val googleusercontent =
        Regex("^(https://(?:lh3|yt3)\\.googleusercontent\\.com/.*)=w(\\d+)-h(\\d+)(.*)$")
            .matchEntire(this)
    if (googleusercontent != null) {
        val originalWidth = googleusercontent.groupValues[2].toInt()
        val originalHeight = googleusercontent.groupValues[3].toInt()
        val resizedWidth = width ?: ((height!! * originalWidth) / originalHeight)
        val resizedHeight = height ?: ((width!! * originalHeight) / originalWidth)
        return "${googleusercontent.groupValues[1]}=w$resizedWidth-h$resizedHeight${googleusercontent.groupValues[4]}"
    }

    val ggpht = Regex("^(https://yt3\\.ggpht\\.com/.*)=s(\\d+)(.*)$").matchEntire(this)
    if (ggpht != null) {
        val requestedWidth = width ?: height!!
        val requestedHeight = height ?: width
        val dimensions =
            if (requestedHeight != null) {
                "w$requestedWidth-h$requestedHeight-p-l90-rj"
            } else {
                "s$requestedWidth-p-l90-rj"
            }
        return "${ggpht.groupValues[1]}=$dimensions${ggpht.groupValues[3]}"
    }

    if (startsWith("https://i.ytimg.com/") && maxOf(width ?: 0, height ?: 0) > 480) {
        return replace("hqdefault.jpg", "maxresdefault.jpg")
            .replace("mqdefault.jpg", "maxresdefault.jpg")
            .replace("sddefault.jpg", "maxresdefault.jpg")
    }

    return this
}

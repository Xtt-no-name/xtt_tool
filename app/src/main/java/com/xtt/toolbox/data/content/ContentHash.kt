package com.xtt.toolbox.data.content

import java.security.MessageDigest

object ContentHash {
    fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString(separator = "") { "%02x".format(it.toInt() and 0xFF) }
    }
}

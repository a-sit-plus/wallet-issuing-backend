package at.asitplus.wallet.backend.data

import java.util.Base64

fun ByteArray.toBase64Url() = Base64.getUrlEncoder().encodeToString(this)

fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }

fun String.fromBase64Url() = Base64.getUrlDecoder().decode(this)

fun String.fromHexString() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()



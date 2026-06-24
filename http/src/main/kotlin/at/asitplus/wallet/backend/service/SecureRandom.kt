package at.asitplus.wallet.backend.service

import java.security.SecureRandom

object SecureRandom {
    private val secureRandom: SecureRandom = SecureRandom()

    fun nextInt(): Int = secureRandom.nextInt()
    fun nextInt(until: Int): Int = secureRandom.nextInt(until)
    fun nextInt(from: Int, until: Int): Int = from + secureRandom.nextInt(until - from)

    fun nextLong(): Long = secureRandom.nextLong()

    fun nextBoolean(): Boolean = secureRandom.nextBoolean()

    fun nextDouble(): Double = secureRandom.nextDouble()

    fun nextFloat(): Float = secureRandom.nextFloat()

    fun nextBytes(array: ByteArray): ByteArray = array.also { secureRandom.nextBytes(it) }
    fun nextBytes(size: Int): ByteArray = ByteArray(size).also { secureRandom.nextBytes(it) }

}
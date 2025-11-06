package at.asitplus.wallet.backend.controller

import at.asitplus.openid.CredentialOffer
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

@Service
class NonceToOfferMap {

    private val map = ConcurrentHashMap<String, Timestamped<CredentialOffer>>()

    fun put(nonce: String, value: CredentialOffer) =
        map.put(nonce, Timestamped(value, Clock.System.now().plus(24.hours)))
            .also { map.entries.removeIf { it.value.expiration < Clock.System.now() } }

    fun get(nonce: String) = map.get(nonce)?.value

}

@Service
class NonceToSessionMap {

    private val map = ConcurrentHashMap<String, Timestamped<String>>()

    fun put(nonce: String, value: String) =
        map.put(nonce, Timestamped(value, Clock.System.now().plus(24.hours)))
            .also { map.entries.removeIf { it.value.expiration < Clock.System.now() } }

    fun get(nonce: String) = map.get(nonce)?.value

    fun remove(nonce: String) = map.remove(nonce)?.value
}


data class Timestamped<T>(val value: T, val expiration: Instant)

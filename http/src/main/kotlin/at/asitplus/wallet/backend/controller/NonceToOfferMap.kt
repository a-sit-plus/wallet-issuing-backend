package at.asitplus.wallet.backend.controller

import at.asitplus.openid.CredentialOffer
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

@Service
class NonceToOfferMap {

    private val map = ConcurrentHashMap<String, TimestampedOffer>()

    fun put(nonce: String, offer: CredentialOffer) =
        map.put(nonce, TimestampedOffer(offer, Clock.System.now().plus(24.hours)))
            .also { map.entries.removeIf { it.value.expiration < Clock.System.now() } }

    fun get(nonce: String) = map.get(nonce)?.offer

    data class TimestampedOffer(val offer: CredentialOffer, val expiration: Instant)

}
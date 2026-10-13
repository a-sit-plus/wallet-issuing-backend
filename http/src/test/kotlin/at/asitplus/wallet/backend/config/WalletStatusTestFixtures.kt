package at.asitplus.wallet.backend.config

import at.asitplus.signum.indispensable.josef.JwsCompactTyped
import at.asitplus.wallet.lib.DefaultZlibService
import at.asitplus.wallet.lib.agent.KeyMaterial
import at.asitplus.wallet.lib.data.StatusListJwt
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.StatusList
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.StatusListTokenPayload
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.primitives.TokenStatusBitSize
import at.asitplus.wallet.lib.data.rfc3986.UniformResourceIdentifier
import at.asitplus.wallet.lib.jws.JwsHeaderCertOrJwk
import at.asitplus.wallet.lib.jws.SignJwt
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

internal suspend fun walletStatusToken(
    signer: KeyMaterial, uri: String, revoked: Boolean = false,
    expiry: Instant = Clock.System.now() + 1.hours,
): StatusListJwt {
    val signed = SignJwt<StatusListTokenPayload>(signer, JwsHeaderCertOrJwk())(
        "statuslist+jwt", StatusListTokenPayload(
            subject = UniformResourceIdentifier(uri), issuedAt = Clock.System.now(), expirationTime = expiry,
            revocationList = StatusList(
                compressed = requireNotNull(DefaultZlibService().compress(byteArrayOf(if (revoked) 0xff.toByte() else 0))),
                statusBitSize = TokenStatusBitSize.ONE,
            ),
        ), StatusListTokenPayload.serializer(),
    ).getOrThrow()
    return StatusListJwt(JwsCompactTyped(signed.jws, signed.payload), Clock.System.now())
}

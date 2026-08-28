package at.asitplus.wallet.backend.config

import at.asitplus.wallet.lib.agent.EphemeralKeyWithoutCert
import at.asitplus.wallet.lib.agent.StatusListAgent
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

class StatusListGroupTest {

    @Test
    fun `slug keeps a credential identifier url safe`() {
        "urn:eudi:pid:1".toStatusListSlug() shouldBe "urn-eudi-pid-1"
        "eu.europa.ec.eudi.pid.1".toStatusListSlug() shouldBe "eu.europa.ec.eudi.pid.1"
    }

    @Test
    fun `unknown configured credential is rejected`() {
        shouldThrow<IllegalArgumentException> {
            requireKnownCredentialIdentifiers(configured = setOf("no.such.credential"), known = setOf("urn:eudi:pid:1"))
        }.message.shouldContain("no.such.credential")
    }

    @Test
    fun `known configured credentials are accepted`() {
        requireKnownCredentialIdentifiers(configured = setOf("urn:eudi:pid:1"), known = setOf("urn:eudi:pid:1"))
    }

    @Test
    fun `credentials sharing a status list path are rejected`() {
        shouldThrow<IllegalArgumentException> {
            StatusListGroups(listOf(group(null), group("urn:eudi:pid:1"), group("urn/eudi/pid/1")))
        }.message.shouldContain("urn-eudi-pid-1")
    }

    private fun group(credentialIdentifier: String?) = StatusListGroup(
        credentialIdentifier = credentialIdentifier,
        slug = credentialIdentifier?.toStatusListSlug() ?: "",
        keyMaterial = EphemeralKeyWithoutCert(),
        statusListAgent = StatusListAgent(),
    )
}

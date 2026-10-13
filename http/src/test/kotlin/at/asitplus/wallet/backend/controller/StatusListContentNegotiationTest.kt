package at.asitplus.wallet.backend.controller

import at.asitplus.signum.indispensable.josef.JwsCompactTyped
import at.asitplus.wallet.backend.Paths
import at.asitplus.wallet.backend.config.BackendConfigurationProperties
import at.asitplus.wallet.backend.config.RevocationListConfigurationProperties
import at.asitplus.wallet.backend.config.StatusListGroup
import at.asitplus.wallet.backend.config.StatusListGroups
import at.asitplus.wallet.backend.service.RevocationListWriter
import at.asitplus.wallet.lib.agent.EphemeralKeyWithSelfSignedCert
import at.asitplus.wallet.lib.agent.StatusListAgent
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.StatusListTokenPayload
import at.asitplus.wallet.lib.jws.VerifyJwsObject
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.nio.file.Path

class StatusListContentNegotiationTest {
    @TempDir lateinit var directory: Path
    private lateinit var mvc: MockMvc
    private val jwt = "application/statuslist+jwt"
    private val cwt = "application/statuslist+cwt"
    private val mdocIdentifier = "eu.europa.ec.eudi.pid.1"
    private val paths = listOf("${Paths.Credentials.StatusUrl}/1", "${Paths.Credentials.StatusUrl}/pid/1")

    @BeforeEach
    fun setup() = runBlocking {
        val configuration = BackendConfigurationProperties(
            revocationList = RevocationListConfigurationProperties(path = directory.toString()),
        )
        val groups = StatusListGroups(listOf(null to null, "pid" to "urn:eudi:pid:1", mdocIdentifier to mdocIdentifier).map { (slug, identifier) ->
            val key = EphemeralKeyWithSelfSignedCert()
            StatusListGroup(identifier, slug.orEmpty(), key, StatusListAgent(
                keyMaterial = key, statusListBaseUrl = "https://issuer.example${Paths.Credentials.StatusUrl}" +
                    if (slug == null) "" else "/$slug",
            ))
        })
        RevocationListWriter(groups, configuration).writeRevocationList(1)
        mvc = MockMvcBuilders.standaloneSetup(StatusListController(groups, configuration)).build()
    }

    @Test
    fun `absent wildcard and equal preferences return correctly labelled signed JWTs`() = runBlocking {
        for (path in paths) for (accept in listOf(null, "*/*", "application/*", "$cwt, $jwt", "$jwt, $cwt")) {
            val response = fetch(path, accept)
            response.status shouldBe 200
            response.contentType shouldBe jwt
            response.getHeader(HttpHeaders.VARY) shouldBe "Accept"
            val token = JwsCompactTyped<StatusListTokenPayload>(response.contentAsString)
            VerifyJwsObject()(token.jws).getOrThrow()
            token.payload.subject.string shouldBe "https://issuer.example$path"
        }
        Unit
    }

    @Test
    fun `mdoc URL defaults to CWT while explicit JWT preferences still win`() {
        val path = "${Paths.Credentials.StatusUrl}/$mdocIdentifier/1"
        for (accept in listOf(null, "*/*", "application/*", "$jwt, $cwt", "$cwt, $jwt")) {
            val response = fetch(path, accept)
            response.status shouldBe 200
            response.contentType shouldBe cwt
            response.contentAsByteArray.take(2).map { it.toInt() and 0xff } shouldBe listOf(0xd2, 0x84)
            response.getHeader(HttpHeaders.VARY) shouldBe "Accept"
        }
        for (accept in listOf(jwt, "$jwt;q=0.9, $cwt;q=0.1", "$cwt;q=0, */*")) {
            fetch(path, accept).contentType shouldBe jwt
        }
    }

    @Test
    fun `quality and specific exclusions determine the returned representation`() {
        val cases = mapOf(
            cwt to cwt,
            "$jwt;q=0.1, $cwt;q=0.9" to cwt,
            "$cwt;q=0.1, $jwt;q=0.9" to jwt,
            "$cwt;q=0, */*" to jwt,
            "$jwt;q=0, */*" to cwt,
            "$cwt, */*" to cwt,
        )
        for (path in paths) for ((accept, type) in cases) {
            val response = fetch(path, accept)
            response.status shouldBe 200
            response.contentType shouldBe type
            if (type == cwt) response.contentAsByteArray.take(2).map { it.toInt() and 0xff } shouldBe listOf(0xd2, 0x84)
        }
    }

    @Test
    fun `unsupported and excluded representations return not acceptable`() {
        for (accept in listOf("application/json", "$jwt;q=0, $cwt;q=0", "*/*;q=0")) {
            fetch(paths.first(), accept).status shouldBe 406
        }
    }

    @Test
    fun `conditional requests retain representation and vary headers`() {
        for (type in listOf(jwt, cwt)) {
            val first = fetch(paths.first(), type)
            val request = get(paths.first()).header(HttpHeaders.ACCEPT, type)
                .header(HttpHeaders.IF_NONE_MATCH, requireNotNull(first.getHeader(HttpHeaders.ETAG)))
            val response = mvc.perform(request).andReturn().response
            response.status shouldBe 304
            response.contentType shouldBe type
            response.getHeader(HttpHeaders.VARY) shouldBe "Accept"
        }
    }

    private fun fetch(path: String, accept: String?) = mvc.perform(
        get(path).apply { if (accept != null) header(HttpHeaders.ACCEPT, accept) },
    ).andReturn().response
}

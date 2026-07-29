package at.asitplus.wallet.backend.config

import at.asitplus.wallet.lib.data.ConstantIndex.CredentialRepresentation.SD_JWT
import at.asitplus.wallet.lib.data.CredentialMetadataRegistry
import at.asitplus.wallet.sdjwt.SelectiveDisclosureConstraints.NEVER
import io.kotest.matchers.nulls.shouldNotBeNull
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * Verifies that the registry resolves both cached remote documents and metadata bundled in main resources.
 */
@SpringBootTest
class CredentialMetadataRegistryTest {

    @Autowired
    private lateinit var registry: CredentialMetadataRegistry

    @Autowired
    private lateinit var credentialOfferings: List<CredentialOffering>

    @Test
    fun `every credential doc resolves from the cached documents`() = runTest {
        CredentialCatalog.entries.forEach { doc ->
            val resolved = registry.findEntry(doc.identifier, doc.representation).shouldNotBeNull()
            assertEquals(doc.vct, resolved.metadata.vct.string) { "${doc.fileName}: vct mismatch" }
        }
    }

    @Test
    fun `credential offerings are resolved at startup from the cached documents`() {
        assertEquals(CredentialCatalog.entries.size, credentialOfferings.size)
        credentialOfferings.forEach { assertTrue(it.name.isNotBlank()) { "${it.scheme}: blank display name" } }
    }

    @Test
    fun `IDA 1_5 binding claims are not selectively disclosable`() = runTest {
        val claims = registry.findEntry(Ida15BindingClaims.VCT, SD_JWT)
            .shouldNotBeNull().metadata.claims.shouldNotBeNull()
        assertTrue(claims.all { it.selectiveDisclosureConstraints == NEVER })
    }
}

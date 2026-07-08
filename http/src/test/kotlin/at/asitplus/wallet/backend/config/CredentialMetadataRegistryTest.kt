package at.asitplus.wallet.backend.config

import at.asitplus.wallet.lib.ktor.openid.RemoteCredentialMetadataRegistry
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * Verifies that the registry works against the cached type metadata documents in `src/test/resources` (served by
 * [CachedTypeMetadataConfiguration]) — i.e. that the snapshot is complete, parses, and matches [CredentialCatalog].
 */
@SpringBootTest
class CredentialMetadataRegistryTest {

    @Autowired
    private lateinit var registry: RemoteCredentialMetadataRegistry

    @Autowired
    private lateinit var credentialOfferings: List<CredentialOffering>

    @Test
    fun `every credential doc resolves from the cached documents`() = runTest {
        CredentialCatalog.entries.forEach { doc ->
            val resolved = registry.findEntry(doc.identifier, doc.representation)
            assertNotNull(resolved) { "${doc.identifier}: no cached document for ${doc.fileName}" }
            assertEquals(doc.vct, resolved!!.metadata.vct.string) { "${doc.fileName}: vct mismatch" }
        }
    }

    @Test
    fun `credential offerings are resolved at startup from the cached documents`() {
        assertEquals(CredentialCatalog.entries.size, credentialOfferings.size)
        credentialOfferings.forEach { assertTrue(it.name.isNotBlank()) { "${it.scheme}: blank display name" } }
    }
}

package at.asitplus.wallet.backend.config

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class RevocationListConfigurationPropertiesTest {

    @Test
    fun `status list size keeps the minimum of free indices in steps of the capacity increment`() {
        with(RevocationListConfigurationProperties(capacityIncrement = 1000, minimumFreeIndices = 500)) {
            statusListSize(0) shouldBe 1000
            statusListSize(1) shouldBe 1000
            // entries 500..999 are still free
            statusListSize(499) shouldBe 1000
            statusListSize(500) shouldBe 2000
            statusListSize(1499) shouldBe 2000
            statusListSize(1500) shouldBe 3000
        }
    }

    @Test
    fun `status list size always covers the highest index`() {
        with(RevocationListConfigurationProperties(capacityIncrement = 8, minimumFreeIndices = 0)) {
            statusListSize(0) shouldBe 8
            statusListSize(7) shouldBe 8
            statusListSize(8) shouldBe 16
        }
    }

    @Test
    fun `invalid capacity configuration is rejected`() {
        shouldThrow<IllegalArgumentException> { RevocationListConfigurationProperties(capacityIncrement = 0) }
        shouldThrow<IllegalArgumentException> { RevocationListConfigurationProperties(minimumFreeIndices = -1) }
    }
}

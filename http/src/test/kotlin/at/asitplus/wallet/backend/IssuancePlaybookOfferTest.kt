package at.asitplus.wallet.backend

import at.asitplus.openid.CredentialOffer
import at.asitplus.signum.indispensable.josef.io.joseCompliantSerializer
import at.asitplus.wallet.backend.controller.IndexController
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class IssuancePlaybookOfferTest {
    @Autowired lateinit var mockMvc: MockMvc

    @Test
    fun `playbook offers use eu-eaa-offer scheme and authorization code for each credential selection`() {
        val initial = mockMvc.get("/").andExpect { request { asyncStarted() } }.andReturn()
        val page = mockMvc.perform(asyncDispatch(initial)).andExpect(status().isOk).andReturn()
        val tabs = page.modelAndView!!.model["tabs"] as List<IndexController.TabItem>

        val selected = tabs.filter {
            it.title.startsWith("PID ·") || it.title.startsWith("mDL ·")
        }
        selected.map { it.title }.shouldContainExactlyInAnyOrder(
            "PID · SD-JWT · auth code",
            "PID · mdoc · auth code",
            "mDL · mdoc · auth code",
            "PID · SD-JWT + mdoc · auth code",
        )
        tabs.any { it.title == "EHIC · SD-JWT · auth code" } shouldBe true
        selected.forEach { tab ->
            tab.preAuth shouldBe false
            tab.offerUrl.startsWith("eu-eaa-offer://?credential_offer_uri=") shouldBe true
            val offerRequest = mockMvc.get("/offer/${tab.id}").andExpect { request { asyncStarted() } }.andReturn()
            val offerResponse = mockMvc.perform(asyncDispatch(offerRequest)).andExpect(status().isOk).andReturn()
            val offer = joseCompliantSerializer.decodeFromString<CredentialOffer>(offerResponse.response.contentAsString)
            (offer.grants?.authorizationCode != null) shouldBe true
            (offer.grants?.preAuthorizedCode == null) shouldBe true
            offer.configurationIds.size shouldBe if (tab.title.contains("+")) 2 else 1
        }
    }
}

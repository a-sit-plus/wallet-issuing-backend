package at.asitplus.wallet.backend

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class IssuerMetadataContentNegotiationTest {
    @Autowired lateinit var mockMvc: MockMvc

    @Test
    fun `issuer metadata is available as a signed JWT for wallet trust validation`() {
        val initial = mockMvc.get("/.well-known/openid-credential-issuer") {
            accept = MediaType.parseMediaType("application/jwt")
        }.andExpect { request { asyncStarted() } }.andReturn()
        val result = mockMvc.perform(asyncDispatch(initial))
            .andExpect(status().isOk)
            .andExpect(content().contentType("application/jwt"))
            .andReturn()
        check(result.response.contentAsString.split('.').size == 3)
    }
}

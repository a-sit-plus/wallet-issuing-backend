package at.asitplus.wallet.backend

import at.asitplus.wallet.lib.oauth2.OAuth2Client
import at.asitplus.wallet.lib.oidvci.encodeToParameters
import at.asitplus.wallet.lib.oidvci.formUrlEncode
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(properties = ["backend.wallet-attestation.enabled=true"])
@AutoConfigureMockMvc
class WalletAttestationParTest {
    @Autowired lateinit var mockMvc: MockMvc

    @Test
    fun `empty Wallet Provider trust set rejects PAR without attestation`() = runBlocking {
        val authRequest = OAuth2Client().createAuthRequest(state = "state", scope = "openid")
        val initial = mockMvc.post(Paths.ParUrl) {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
            content = authRequest.encodeToParameters().formUrlEncode()
        }.andExpect { request { asyncStarted() } }.andReturn()

        mockMvc.perform(asyncDispatch(initial))
            .andExpect(status().isBadRequest)
            .andExpect(content().string(containsString("invalid_client")))
    }
}

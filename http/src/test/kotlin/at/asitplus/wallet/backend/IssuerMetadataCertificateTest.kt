package at.asitplus.wallet.backend

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.Base64

@SpringBootTest(properties = ["backend.metadata-key.type=MEMORY"])
@AutoConfigureMockMvc
class IssuerMetadataCertificateTest {
    @Autowired lateinit var mockMvc: MockMvc

    @Test
    fun `configured metadata key places its certificate in signed metadata`() {
        val initial = mockMvc.get("/.well-known/openid-credential-issuer") {
            accept = MediaType.parseMediaType("application/jwt")
        }.andExpect { request { asyncStarted() } }.andReturn()
        val response = mockMvc.perform(asyncDispatch(initial)).andExpect(status().isOk).andReturn().response
        val header = String(Base64.getUrlDecoder().decode(response.contentAsString.substringBefore('.')))
        check(Json.parseToJsonElement(header).jsonObject["x5c"]!!.jsonArray.isNotEmpty())
    }
}

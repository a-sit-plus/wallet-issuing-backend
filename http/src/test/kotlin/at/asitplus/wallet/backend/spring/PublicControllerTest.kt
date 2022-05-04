package at.asitplus.wallet.backend.spring

import org.hamcrest.Matchers.emptyString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
class PublicControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `GET VC status list`() {
        mockMvc.get("/credentials/status/1") {
        }.andExpect {
            status { isOk() }
            content { string(not(emptyString())) }
        }.andReturn()
    }

    @Test
    fun `GET PKI CRL`() {
        mockMvc.get("/crl/1") {
        }.andExpect {
            status { isOk() }
        }.andReturn()
    }

}
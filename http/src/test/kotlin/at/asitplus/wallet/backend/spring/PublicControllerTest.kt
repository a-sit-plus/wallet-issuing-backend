package at.asitplus.wallet.backend.spring

import at.asitplus.wallet.lib.agent.TimePeriodProvider
import kotlinx.datetime.Clock
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

    @Autowired
    private lateinit var timePeriodProvider: TimePeriodProvider

    @Test
    fun `GET VC status list with valid period`() {
        mockMvc.get("/credentials/status/${timePeriodProvider.getRelevantTimePeriods(Clock.System).first()}") {
        }.andExpect {
            status { isOk() }
            content { string(not(emptyString())) }
        }.andReturn()
    }

    @Test
    fun `GET VC status list with invalid period`() {
        mockMvc.get("/credentials/status/${timePeriodProvider.getRelevantTimePeriods(Clock.System).first() * 2}") {
        }.andExpect {
            status { isNotFound() }
            content { string(emptyString()) }
        }.andReturn()
    }

    @Test
    fun `GET list of currently active VC status lists`() {
        mockMvc.get("/credentials/status/current") {
        }.andExpect {
            status { isOk() }
            content { string(not(emptyString())) }
        }.andReturn()
    }

}
package at.asitplus.wallet.backend.spring

import at.asitplus.wallet.lib.agent.TimePeriodProvider
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.MediaTypes
import kotlinx.datetime.Clock
import org.hamcrest.Matchers.emptyString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
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
    fun `GET CWT status list with If-None-Match in second request`() {
        Thread.sleep(1000L) // wait for RevocationListScheduler to write the revocation list
        val timePeriod = timePeriodProvider.getRelevantTimePeriods(Clock.System).first()
        val firstResult = mockMvc.get("/credentials/status/$timePeriod") {
            accept(MediaType.parseMediaType(MediaTypes.Application.STATUSLIST_CWT))
        }.andExpect {
            status { isOk() }
            content { string(not(emptyString())) }
            header { stringValues(HttpHeaders.CONTENT_TYPE, MediaTypes.Application.STATUSLIST_CWT) }
            header { exists(HttpHeaders.ETAG) }
            header { exists(HttpHeaders.CACHE_CONTROL) }
        }.andReturn()

        mockMvc.get("/credentials/status/$timePeriod") {
            accept(MediaType.parseMediaType(MediaTypes.Application.STATUSLIST_CWT))
            headers {
                ifNoneMatch = listOf(firstResult.response.getHeader(HttpHeaders.ETAG))
            }
        }.andExpect {
            status { isNotModified() }
            content { string(emptyString()) }
            header { stringValues(HttpHeaders.CONTENT_TYPE, MediaTypes.Application.STATUSLIST_CWT) }
            header { exists(HttpHeaders.ETAG) }
            header { exists(HttpHeaders.CACHE_CONTROL) }
        }.andReturn()
    }

    @Test
    fun `GET JWT status list token, with If-Modified-Since in second request`() {
        Thread.sleep(1000L) // wait for RevocationListScheduler to write the revocation list
        val timePeriod = timePeriodProvider.getRelevantTimePeriods(Clock.System).first()
        val firstResult = mockMvc.get("/credentials/status/$timePeriod") {
            accept(MediaType.parseMediaType(MediaTypes.Application.STATUSLIST_JWT))
        }.andExpect {
            status { isOk() }
            content { string(not(emptyString())) }
            header { stringValues(HttpHeaders.CONTENT_TYPE, MediaTypes.Application.STATUSLIST_JWT) }
            header { exists(HttpHeaders.LAST_MODIFIED) }
            header { exists(HttpHeaders.CACHE_CONTROL) }
        }.andReturn()

        mockMvc.get("/credentials/status/$timePeriod") {
            accept(MediaType.parseMediaType(MediaTypes.Application.STATUSLIST_JWT))
            headers {
                ifModifiedSince = firstResult.response.getDateHeader(HttpHeaders.LAST_MODIFIED)
            }
        }.andExpect {
            status { isNotModified() }
            content { string(emptyString()) }
            header { stringValues(HttpHeaders.CONTENT_TYPE, MediaTypes.Application.STATUSLIST_JWT) }
            header { exists(HttpHeaders.LAST_MODIFIED) }
            header { exists(HttpHeaders.CACHE_CONTROL) }
        }.andReturn()
    }

    @Test
    fun `GET status list with invalid period`() {
        mockMvc.get("/credentials/status/${timePeriodProvider.getRelevantTimePeriods(Clock.System).max() * 2}") {
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
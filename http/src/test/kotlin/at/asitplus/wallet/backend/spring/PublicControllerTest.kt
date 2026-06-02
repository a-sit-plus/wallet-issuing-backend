package at.asitplus.wallet.backend.spring

import at.asitplus.signum.indispensable.josef.io.joseCompliantSerializer
import at.asitplus.wallet.backend.Paths
import at.asitplus.wallet.lib.agent.TimePeriodProvider
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.MediaTypes
import at.asitplus.wallet.lib.data.rfc.tokenStatusList.StatusListAggregation
import io.kotest.matchers.nulls.shouldNotBeNull
import org.hamcrest.Matchers.emptyString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import kotlin.time.Clock

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
        val firstResult = mockMvc.get("${Paths.Credentials.StatusUrl}/$timePeriod") {
            accept = MediaType.parseMediaType(MediaTypes.Application.STATUSLIST_CWT)
        }.andExpect {
            status { isOk() }
            content { contentType(MediaType.parseMediaType(MediaTypes.Application.STATUSLIST_CWT)) }
            header { exists(HttpHeaders.ETAG) }
            header { exists(HttpHeaders.CACHE_CONTROL) }
            content { string(not(emptyString())) }
        }.andReturn()

        mockMvc.get("${Paths.Credentials.StatusUrl}/$timePeriod") {
            accept = MediaType.parseMediaType(MediaTypes.Application.STATUSLIST_CWT)
            header(HttpHeaders.IF_NONE_MATCH, firstResult.response.getHeader(HttpHeaders.ETAG).orEmpty())
        }.andExpect {
            status { isNotModified() }
            content { contentType(MediaType.parseMediaType(MediaTypes.Application.STATUSLIST_CWT)) }
            header { exists(HttpHeaders.ETAG) }
            header { exists(HttpHeaders.CACHE_CONTROL) }
            content { string("") }
        }
    }

    @Test
    fun `GET JWT status list token, with If-Modified-Since in second request`() {
        Thread.sleep(1000L) // wait for RevocationListScheduler to write the revocation list
        val timePeriod = timePeriodProvider.getRelevantTimePeriods(Clock.System).first()
        val firstResult = mockMvc.get("${Paths.Credentials.StatusUrl}/$timePeriod") {
            accept = MediaType.parseMediaType(MediaTypes.Application.STATUSLIST_JWT)
        }.andExpect {
            status { isOk() }
            content { contentType(MediaType.parseMediaType(MediaTypes.Application.STATUSLIST_JWT)) }
            header { exists(HttpHeaders.LAST_MODIFIED) }
            header { exists(HttpHeaders.CACHE_CONTROL) }
            content { string(not(emptyString())) }
        }.andReturn()

        mockMvc.get("${Paths.Credentials.StatusUrl}/$timePeriod") {
            accept = MediaType.parseMediaType(MediaTypes.Application.STATUSLIST_JWT)
            header(HttpHeaders.IF_MODIFIED_SINCE, firstResult.response.getHeader(HttpHeaders.LAST_MODIFIED).orEmpty())
        }.andExpect {
            status { isNotModified() }
            content { contentType(MediaType.parseMediaType(MediaTypes.Application.STATUSLIST_JWT)) }
            header { exists(HttpHeaders.LAST_MODIFIED) }
            header { exists(HttpHeaders.CACHE_CONTROL) }
            content { string("") }
        }
    }

    @Test
    fun `GET status list with invalid period`() {
        mockMvc.get("${Paths.Credentials.StatusUrl}/${timePeriodProvider.getRelevantTimePeriods(Clock.System).max() * 2}")
            .andExpect {
                status { isNotFound() }
                content { string("") }
            }
    }

    @Test
    fun `GET list of currently active VC status lists`() {
        val asyncResult = mockMvc.get(Paths.Credentials.Status.CurrentUrl)
            .andExpect {
                request { asyncStarted() }
            }
            .andReturn()

        val result = mockMvc.perform(asyncDispatch(asyncResult))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().string(not(emptyString())))
            .andExpect(jsonPath("$").isMap)
            .andExpect(jsonPath("$.status_lists").isArray)
            .andReturn()

        joseCompliantSerializer.decodeFromString<StatusListAggregation>(
            result.response.contentAsString.shouldNotBeNull()
        )
    }
}

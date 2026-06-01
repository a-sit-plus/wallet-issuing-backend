package at.asitplus.wallet.backend.spring

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

/**
 * Actuator endpoints are blocked when Spring Boot Admin client is not configured
 * (spring.boot.admin.client.enabled defaults to false).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ActuatorSecurityDisabledTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `actuator is blocked without SBA config`() {
        mockMvc.get("/actuator/health").andExpect {
            status { isForbidden() }
        }
    }

    @Test
    fun `demo user cannot access actuator without SBA config`() {
        mockMvc.get("/actuator/health") {
            with(httpBasic("user", "password"))
        }.andExpect {
            status { isForbidden() }
        }
    }
}

/**
 * Actuator endpoints require HTTP Basic auth using the SBA metadata credentials when
 * spring.boot.admin.client.enabled=true and both user.name/user.password are set.
 */
@SpringBootTest(
    properties = [
        "spring.boot.admin.client.enabled=true",
        "spring.boot.admin.client.instance.metadata.user.name=actuator",
        "spring.boot.admin.client.instance.metadata.user.password=secret",
        "spring.security.user.name=user",
        "spring.security.user.password=password",
    ]
)
@AutoConfigureMockMvc
class ActuatorSecurityEnabledTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `actuator accessible with valid SBA credentials`() {
        mockMvc.get("/actuator/health") {
            with(httpBasic("actuator", "secret"))
        }.andExpect {
            status { isOk() }
        }
    }

    @Test
    fun `unauthenticated request returns 401 when SBA is configured`() {
        mockMvc.get("/actuator/health").andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `wrong password returns 401`() {
        mockMvc.get("/actuator/health") {
            with(httpBasic("actuator", "wrong"))
        }.andExpect {
            status { isUnauthorized() }
        }
    }

    @Test
    fun `demo user cannot access actuator even when SBA is configured`() {
        mockMvc.get("/actuator/health") {
            with(httpBasic("user", "password"))
        }.andExpect {
            status { isForbidden() }
        }
    }
}

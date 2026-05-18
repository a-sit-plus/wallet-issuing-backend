package at.asitplus.wallet.backend.spring

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

/**
 * No demo user exists when spring.security.user.password is not set (the default).
 */
@SpringBootTest
@AutoConfigureMockMvc
class DemoUserNotConfiguredTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `login fails when no demo user is configured`() {
        mockMvc.post("/login") {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
            param("username", "user")
            param("password", "password")
        }.andExpect {
            status { is3xxRedirection() }
            redirectedUrl("/login?error")
        }
    }
}

/**
 * Demo user is active only when spring.security.user.password is explicitly configured.
 */
@SpringBootTest(
    properties = [
        "spring.security.user.name=alice",
        "spring.security.user.password=secret",
    ]
)
@AutoConfigureMockMvc
class DemoUserConfiguredTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `configured user can log in`() {
        mockMvc.post("/login") {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
            param("username", "alice")
            param("password", "secret")
        }.andExpect {
            status { is3xxRedirection() }
            redirectedUrl("/")
        }
    }

    @Test
    fun `login fails with wrong password`() {
        mockMvc.post("/login") {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
            param("username", "alice")
            param("password", "wrong")
        }.andExpect {
            status { is3xxRedirection() }
            redirectedUrl("/login?error")
        }
    }

    @Test
    fun `login fails with unconfigured username`() {
        mockMvc.post("/login") {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
            param("username", "user")
            param("password", "secret")
        }.andExpect {
            status { is3xxRedirection() }
            redirectedUrl("/login?error")
        }
    }
}

package at.asitplus.wallet.backend

import at.asitplus.wallet.backend.data.Agent
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class ApiController {

    @Autowired
    lateinit var issuer: Agent

    @GetMapping("/issue")
    fun issueCredential(@RequestParam keyId: String): ResponseEntity<String> {
        val vcSerialized = issuer.issueCredential(keyId)
        return ResponseEntity.ok(vcSerialized.compactJws)
    }

}
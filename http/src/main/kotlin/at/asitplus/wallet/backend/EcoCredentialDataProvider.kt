package at.asitplus.wallet.backend

import at.asitplus.wallet.lib.agent.IssuerCredentialDataProvider
import at.asitplus.wallet.lib.data.ConstantIndex
import at.asitplus.wallet.lib.data.CredentialSubject
import at.asitplus.wallet.lib.data.PupilIdCredential
import org.slf4j.LoggerFactory
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.getForEntity
import kotlin.time.Duration


/**
 * Gets credentials for the currently authenticated user from
 * the connection to the external webservice at ECO,
 * i.e. it looks up data with the `bpk` from a remote service
 */
class EcoCredentialDataProvider constructor(
    private val lifetime: Duration,
    private val url: String,
    private val restTemplate: RestTemplate,
    private val deviceBindingStorageService: DeviceBindingStorageService,
) : IssuerCredentialDataProvider {

    private val log = LoggerFactory.getLogger(this.javaClass)

    override fun getClaim(subjectId: String, attributeName: String): CredentialSubject? {
        return null // not supported for ECO
    }

    override fun getCredential(subjectId: String, attributeType: String): CredentialSubject? {
        if (attributeType != ConstantIndex.PupilId.vcType)
            return null

        val deviceBinding = deviceBindingStorageService.getDeviceBindingForCurrentUser()
            ?: return null.also {
                log.error("Got no authenticated user when trying to issue credentials")
            }
        val entity = restTemplate.getForEntity<EcoStudentData>(
            "$url/Student/{bpk}",
            uriVariables = mapOf("bpk" to deviceBinding.bpk)
        )
        log.debug("getCredential for '{}' got {}", deviceBinding.bpk, entity)

        return entity.body?.let {
            PupilIdCredential(
                id = subjectId,
                schoolName = "Musterschule",
                schoolAddress = "Musterstraße 1",
                schoolNumber = (1..6).map { "01".random() }.joinToString(""),
                pupilNumber = (1..2).joinToString("/") { (1..8).map { "0123456789".random() }.joinToString("") },
                firstName = it.firstname,
                lastName = it.lastname,
                dateOfBirth = "2001-02-28",
                validUntil = "2023-09-01",
                postCity = "Musterstadt",
                postCode = "1010",
                picture = byteArrayOf(),
            )
        }
    }

    override fun getLifetime(): Duration {
        return lifetime
    }

    data class EcoStudentData(
        val firstname: String,
        val lastname: String
    )

}
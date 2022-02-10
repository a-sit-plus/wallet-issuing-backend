package at.asitplus.wallet.backend

import org.springframework.stereotype.Service

@Service
class DeviceBindingStorageService {

    private val map = mutableMapOf<String, ByteArray>()

    fun store(bpk: String, certificate: ByteArray) {
        map[bpk] = certificate
    }

    fun lookupBpk(decodedCert: ByteArray): String? {
        return map.filterValues { it.contentEquals(decodedCert) }.keys.singleOrNull()
    }

}
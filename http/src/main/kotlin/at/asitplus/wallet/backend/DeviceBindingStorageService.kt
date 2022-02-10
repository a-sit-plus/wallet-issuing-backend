package at.asitplus.wallet.backend

interface DeviceBindingStorageService {

    fun store(bpk: String, certificate: ByteArray)

    fun lookupBpk(decodedCert: ByteArray): String?

}

class InMemoryDeviceBindingStorageService : DeviceBindingStorageService {

    private val map = mutableMapOf<String, ByteArray>()

    override fun store(bpk: String, certificate: ByteArray) {
        map[bpk] = certificate
    }

    override fun lookupBpk(decodedCert: ByteArray): String? {
        return map.filterValues { it.contentEquals(decodedCert) }.keys.singleOrNull()
    }

}
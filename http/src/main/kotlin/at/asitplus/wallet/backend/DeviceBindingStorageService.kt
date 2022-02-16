package at.asitplus.wallet.backend

import java.util.UUID

interface DeviceBindingStorageService {

    fun store(bpk: String, certificate: ByteArray, deviceName: String)

    fun lookupBpk(decodedCert: ByteArray): String?

}

class InMemoryDeviceBindingStorageService : DeviceBindingStorageService {

    private val list = mutableListOf<Entry>()

    override fun store(bpk: String, certificate: ByteArray, deviceName: String) {
        list.add(Entry(bpk, certificate, deviceName, UUID.randomUUID().toString()))
    }

    override fun lookupBpk(decodedCert: ByteArray): String? {
        return list.find { it.certificate.contentEquals(decodedCert) }?.bpk
    }

    data class Entry(
        val bpk: String,
        val certificate: ByteArray,
        val deviceName: String,
        val deviceId: String,
    )

}
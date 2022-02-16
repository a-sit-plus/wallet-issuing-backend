package at.asitplus.wallet.backend

import java.util.UUID

interface DeviceBindingStorageService {

    fun store(bpk: String, certificate: ByteArray, deviceName: String)

    fun lookupBpk(decodedCert: ByteArray): String?

    fun lookupDevices(bpk: String): Collection<DeviceListEntry>?

}

data class DeviceListEntry(
    val deviceName: String,
    val deviceId: String,
)

class InMemoryDeviceBindingStorageService : DeviceBindingStorageService {

    private val list = mutableListOf<Entry>()

    override fun store(bpk: String, certificate: ByteArray, deviceName: String) {
        list.add(Entry(bpk, certificate, deviceName, UUID.randomUUID().toString()))
    }

    override fun lookupBpk(decodedCert: ByteArray): String? {
        return list.find { it.certificate.contentEquals(decodedCert) }?.bpk
    }

    override fun lookupDevices(bpk: String): Collection<DeviceListEntry> {
        return list.filter { it.bpk == bpk }
            .map { DeviceListEntry(it.deviceName, it.deviceId) }
    }

    data class Entry(
        val bpk: String,
        val certificate: ByteArray,
        val deviceName: String,
        val deviceId: String,
    )

}
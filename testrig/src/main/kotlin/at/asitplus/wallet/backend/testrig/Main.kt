package at.asitplus.wallet.backend.testrig

import at.asitplus.KmmResult
import at.asitplus.wallet.lib.agent.*
import at.asitplus.wallet.lib.data.ConstantIndex
import at.asitplus.wallet.lib.jws.DefaultJwsService
import at.asitplus.wallet.lib.jws.EcCurve
import at.asitplus.wallet.lib.jws.JwsAlgorithm
import at.asitplus.wallet.lib.jws.JwsHeader
import at.asitplus.wallet.pupilid.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.context.event.EventListener
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.EnumerablePropertySource
import org.springframework.stereotype.Component
import java.net.URL
import java.security.KeyPairGenerator
import java.security.Security
import kotlin.math.roundToInt


@ConstructorBinding
@ConfigurationProperties(prefix = "testrig")
data class TestRigConfProps(
    val runs: Int,
    val parallelism: Int,
    val host: Host,
    val fakeEco: FakeEco,
    val printErrors: Boolean = false,
    val printCredentials: Boolean = false,
) {
    init {
        if (parallelism > runs) throw RuntimeException()
    }

    @ConstructorBinding
    data class Host(val baseURL: URL)

    @ConstructorBinding
    data class FakeEco(val port: Int)
}


const val FAKE_NONCE = "FAKE_NONCE-"

@SpringBootApplication(scanBasePackages = ["at.asitplus.wallet.backend.testrig"])
@EnableConfigurationProperties(TestRigConfProps::class)
@ConstructorBinding
class TestRig(private val cfg: TestRigConfProps) : CommandLineRunner {

    override fun run(vararg args: String?) {
        Security.addProvider(BouncyCastleProvider())
        FakeECO(cfg.fakeEco.port)
        println("Fake ECO Running")
        val runs = List(cfg.runs) { it }.chunked(cfg.runs / cfg.parallelism)
        runBlocking {

            //delay(1000)
            val kp = KeyPairGenerator.getInstance("EC", "BC").also { it.initialize(EcCurve.SECP_256_R_1.keyLengthBits) }
                .genKeyPair()
            val encodedPubKey = kp.public.encoded
            val before = Clock.System.now()
            val jobs = runs.map { list ->
                async {
                    list.map { it to DefaultCryptoService(kp) }.map singleRun@{ (i, cryptoService) ->
                        val certMut = Mutex(locked = true)
                        var cert: ByteArray? = null
                        val bindingService =
                            DeviceBindingService(
                                cfg.host.baseURL.toString(),
                                "$FAKE_NONCE$i",
                                object : DeviceAdapter {
                                    override suspend fun loadAttestationCerts(
                                        challenge: ByteArray,
                                        clientData: ByteArray
                                    ): KmmResult<List<ByteArray>> {
                                        return KmmResult.success(listOf(byteArrayOf()))
                                    }

                                    override fun storeCertificate(
                                        certificate: ByteArray,
                                        attestedPublicKey: String?
                                    ): KmmResult<Boolean> {
                                        cert = certificate
                                        certMut.unlock()
                                        return KmmResult.success(true)
                                    }

                                    override suspend fun createKey(
                                        key: KeyAlgorithm,
                                        challenge: ByteArray
                                    ): KmmResult<Boolean> {
                                        return KmmResult.success(true)
                                    }

                                    override fun getPublicKeyEncoded(): KmmResult<ByteArray> {
                                        return KmmResult.success(encodedPubKey)
                                    }

                                    override val deviceName: String = "testrig-$i"


                                },
                                object : Asn1Service.CryptoAdapter {
                                    override suspend fun sign(
                                        input: ByteArray,
                                        key: KeyAlgorithm,
                                        hash: HashAlgorithm
                                    ): KmmResult<ByteArray> = cryptoService.sign(input)
                                })


                        when (val res = bindingService.createDeviceBinding()) {
                            is ServiceResult.Success -> res.xAuthToken
                            else -> {
                                if (cfg.printErrors) System.err.print("run $i binding error: $res")
                                return@singleRun i to false
                            }
                        }




                        return@singleRun issueCredential(cryptoService, i, certMut.withLock { cert!! })
                    }
                }
            }
            var running = true
            val job = launch {
                val spinner = listOf(
                    "( 🏐    )",
                    "(  🏐   )",
                    "(   🏐  )",
                    "(    🏐 )",
                    "(     🏐)",
                    "(    🏐 )",
                    "(   🏐  )",
                    "(  🏐   )",
                    "( 🏐    )",
                    "(🏐     )"
                )
                println()
                while (running) {
                    spinner.forEach {
                        print("\r$it Requesting Credentials")
                        delay(100)
                    }
                }
            }
            val result = jobs.awaitAll().flatten().toMap()
            val timeTaken = Clock.System.now() - before
            running = false
            val succeeded = result.count { (_, state) -> state }
            val failed = result.count { (_, state) -> !state }
            val successRatio = ((succeeded - failed).toFloat() / (succeeded.toFloat()) * 100).roundToInt()
            job.join()
            if (successRatio == 100) {
                print("\r\uD83C\uDF8A\uD83C\uDF89\uD83C\uDF89\uD83C\uDF89\uD83C\uDF89\uD83C\uDF89\uD83C\uDF89\uD83C\uDF8A All done")
            } else {
                print("\r\uD83D\uDD25\uD83D\uDCA5\uD83D\uDCA5\uD83D\uDCA5\uD83D\uDCA5\uD83D\uDCA5\uD83D\uDCA5\uD83D\uDD25 All done with errors")
            }
            println("\nTotal Tests: ${result.size}\t success: $succeeded, failed: $failed\t(success ratio: ~$successRatio%)")
            println("Took ${timeTaken.inWholeSeconds} seconds to try and issue ${cfg.runs} credentials (=${cfg.runs.toDouble() / timeTaken.inWholeSeconds.toDouble()} requests per second)")


        }
    }

    private suspend fun issueCredential(
        cryptoService: DefaultCryptoService,
        i: Int,
        cert: ByteArray
    ): Pair<Int, Boolean> {
        val issuingService =
            PupilIdIssuingService(cfg.host.baseURL.toString(), { payload ->
                KmmResult.success(
                    DefaultJwsService(cryptoService).createSignedJws(
                        JwsHeader(
                            JwsAlgorithm.ES256,
                            certificateChain = arrayOf(cert)
                        ),
                        payload.encodeToByteArray()
                    )!!
                )
            })
        val messenger = IssueCredentialMessenger.newHolderInstance(
            holder = HolderAgent.newDefaultInstance(
                cryptoService = cryptoService,
                verifierCryptoService = DefaultVerifierCryptoService(),
                subjectCredentialStore = InMemorySubjectCredentialStore(),
                Clock.System
            ),
            keyId = cryptoService.keyId,
            messageWrapper = MessageWrapper(DefaultCryptoService()),
            credentialScheme = ConstantIndex.PupilId
        )


        val message = messenger.startDirect()
        if (message !is NextMessage.Send) TODO()
        val issueCredentials = issuingService.issueCredentials(message.message)
        return if (issueCredentials !is ServiceResult.Success)
            i to false.also { if (cfg.printErrors) System.err.println("run $i issueCredentialError: $issueCredentials") }
        else
            i to true.also { if (cfg.printCredentials) println(issueCredentials.toString()) }
    }
}


@Component
class AppContextEventListener {
    companion object {
        private val logger = LoggerFactory.getLogger(AppContextEventListener::class.java)
    }

    @EventListener
    fun handleContextRefreshed(event: ContextRefreshedEvent) {
        printActiveProperties(event.applicationContext.environment as ConfigurableEnvironment)
    }

    fun printActiveProperties(env: ConfigurableEnvironment) {
        logger.info("************************* ACTIVE APP PROPERTIES ******************************")
        env.propertySources
            .asSequence()
            .filter { it.name.contains("application") }
            .map { it as EnumerablePropertySource<*> }
            .map { it.propertyNames.toList() }
            .flatten()
            .distinctBy { it }
            .sortedBy { it }
            .toList()
            .forEach {
                try {
                    if (it.contains("password", ignoreCase = true) || it.contains(
                            "api-key",
                            ignoreCase = true
                        )
                    ) {
                        logger.info("$it=***")
                    } else {
                        logger.info("$it=${env.getProperty(it)}")
                    }

                } catch (e: Exception) {
                    logger.warn("$it -> ${e.message}")
                }
            }
        logger.info("******************************************************************************")
    }
}

fun main(args: Array<String>) {
    runApplication<TestRig>(*args)
}


private val HashAlgorithm.jcaName: String
    get() = when (this) {
        HashAlgorithm.SHA1 -> "SHA1"
        HashAlgorithm.SHA256 -> "SHA256"
        HashAlgorithm.SHA512 -> "SHA512"
    }

private val KeyAlgorithm.jcaName: String
    get() = when (this) {
        KeyAlgorithm.EC -> "ECDSA"
        KeyAlgorithm.RSA -> "RSA"
    }
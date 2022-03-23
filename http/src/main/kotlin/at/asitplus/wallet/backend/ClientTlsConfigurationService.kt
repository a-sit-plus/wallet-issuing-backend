package at.asitplus.wallet.backend

import at.asitplus.wallet.lib.encodeBase64
import org.apache.http.conn.ssl.SSLConnectionSocketFactory
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.message.BasicHeader
import org.apache.http.ssl.SSLContextBuilder
import org.apache.http.ssl.SSLContexts
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.web.client.RestTemplate
import java.security.KeyStore


class ClientTlsConfigurationService constructor(
    configuration: ExternalTlsConnection,
    restTemplateBuilder: RestTemplateBuilder,
) {

    val restTemplate: RestTemplate

    init {
        val httpClient = when {
            configuration.serverTls -> buildHttpClientTls(configuration)
            else -> buildHttpClientBasicAuth(configuration.httpBasic!!)
        }
        val requestFactory = HttpComponentsClientHttpRequestFactory(httpClient)
        restTemplate = restTemplateBuilder.requestFactory { requestFactory }.build()
    }

    private fun buildHttpClientTls(configuration: ExternalTlsConnection): CloseableHttpClient {
        val sslContextBuilder = SSLContexts.custom()
        when (configuration.trust.type) {
            TrustType.KEYSTORE -> loadTrustStore(sslContextBuilder, configuration.trust.truststore!!)
            else -> {} // load nothing
        }
        if (configuration.clientTls && configuration.key != null) {
            when (configuration.key!!.type) {
                KeyType.KEYSTORE -> loadKeyStore(sslContextBuilder, configuration.key!!.keystore!!)
                else -> throw IllegalArgumentException("key not configured")
            }
        }
        val sslContext = sslContextBuilder.build()
        return HttpClients.custom().setSSLSocketFactory(SSLConnectionSocketFactory(sslContext)).build()
    }

    private fun loadKeyStore(sslContextBuilder: SSLContextBuilder, config: KeyStoreConfiguration) {
        val keyStore = KeyStore.getInstance(config.type, config.provider).also {
            it.load(config.path.toURL().openStream(), config.password?.toCharArray() ?: charArrayOf())
        }
        sslContextBuilder.loadKeyMaterial(keyStore, config.aliasPassword?.toCharArray() ?: charArrayOf())
    }

    private fun loadTrustStore(sslContextBuilder: SSLContextBuilder, config: TrustStoreConfiguration) {
        val trustStore = KeyStore.getInstance(config.type, config.provider).also {
            it.load(config.path.toURL().openStream(), config.password?.toCharArray() ?: charArrayOf())
        }
        sslContextBuilder.loadTrustMaterial(trustStore, null)
    }

    private fun buildHttpClientBasicAuth(config: HttpBasicAuthnConfigurationProperties): CloseableHttpClient {
        val auth = "${config.username}:${config.password}".encodeToByteArray().encodeBase64()
        val headers = listOf(BasicHeader("Authorization", "Basic ${auth}"))
        return HttpClients.custom().setDefaultHeaders(headers).build()
    }

}
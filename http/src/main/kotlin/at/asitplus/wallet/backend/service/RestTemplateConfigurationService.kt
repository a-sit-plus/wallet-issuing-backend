package at.asitplus.wallet.backend.service

import at.asitplus.wallet.backend.auth.WebSecurityConstants
import at.asitplus.wallet.backend.auth.WebSecurityConstants.X_API_KEY
import at.asitplus.wallet.backend.config.*
import at.asitplus.wallet.lib.encodeBase64
import org.apache.http.conn.ssl.SSLConnectionSocketFactory
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.message.BasicHeader
import org.apache.http.ssl.SSLContextBuilder
import org.apache.http.ssl.SSLContexts
import org.slf4j.LoggerFactory
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.HttpMethod
import org.springframework.http.client.ClientHttpResponse
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.web.client.DefaultResponseErrorHandler
import org.springframework.web.client.RestTemplate
import java.net.URI
import java.security.KeyStore


/**
 * Configures an instance of [RestTemplate] according to configuration from [ExternalConnectionConfig],
 * to be used to consume a REST API at an external service.
 */
class RestTemplateConfigurationService constructor(
    config: ExternalConnectionConfig,
    restTemplateBuilder: RestTemplateBuilder,
) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    val restTemplate: RestTemplate

    init {
        val httpClient = when {
            config.serverTls -> buildHttpClientTls(config)
            else -> buildHttpClientBasicAuth(config.httpBasic!!, config.url)
        }
        val requestFactory = HttpComponentsClientHttpRequestFactory(httpClient)
        restTemplate = restTemplateBuilder
            .requestFactory { requestFactory }
            .errorHandler(LoggingErrorHandler())
            .build()
    }

    inner class LoggingErrorHandler : DefaultResponseErrorHandler() {
        override fun handleError(url: URI, method: HttpMethod, response: ClientHttpResponse) {
            log.error(
                "URL '{}', method '{}' got response statusCode '{}': {}",
                url, method, response.statusCode, response.statusText
            )
            super.handleError(url, method, response)
        }
    }

    private fun buildHttpClientTls(config: ExternalConnectionConfig): CloseableHttpClient {
        val httpClientBuilder = HttpClients.custom()
        val sslContextBuilder = SSLContexts.custom()
        when (config.trust?.type) {
            TrustType.KEYSTORE -> loadTrustStore(sslContextBuilder, config.trust!!.truststore!!, config.url)
            else -> {} // load nothing
        }
        if (config.clientTls && config.key != null) {
            when (config.key!!.type) {
                KeyType.KEYSTORE -> loadKeyStore(sslContextBuilder, config.key!!.keystore!!, config.url)
                else -> throw IllegalArgumentException("key not configured")
            }
        }
        if (config.apiKey != null) {
            log.info("Setting api key 'MASKED' for {}", config.url)
            httpClientBuilder.setDefaultHeaders(listOf(BasicHeader(X_API_KEY, config.apiKey)))
        }
        val sslContext = sslContextBuilder.build()
        return httpClientBuilder.setSSLSocketFactory(SSLConnectionSocketFactory(sslContext)).build()
    }

    private fun loadKeyStore(sslContextBuilder: SSLContextBuilder, config: KeyStoreConfiguration, url: URI?) {
        log.info("Loading key store from {} for {}", config.path, url)
        val keyStore = KeyStore.getInstance(config.type, config.provider).also {
            it.load(config.path.toURL().openStream(), config.password?.toCharArray() ?: charArrayOf())
        }
        sslContextBuilder.loadKeyMaterial(keyStore, config.aliasPassword?.toCharArray() ?: charArrayOf())
    }

    private fun loadTrustStore(sslContextBuilder: SSLContextBuilder, config: TrustStoreConfiguration, url: URI?) {
        log.info("Loading trust store from {} for {}", config.path, url)
        val trustStore = KeyStore.getInstance(config.type, config.provider).also {
            it.load(config.path.toURL().openStream(), config.password?.toCharArray() ?: charArrayOf())
        }
        sslContextBuilder.loadTrustMaterial(trustStore, null)
    }

    private fun buildHttpClientBasicAuth(
        config: HttpBasicAuthnConfigurationProperties,
        url: URI?
    ): CloseableHttpClient {
        log.info("Loading HTTP basic authn with '{}' for {}", config.username, url)
        val auth = "${config.username}:${config.password}".encodeToByteArray().encodeBase64()
        val headers = listOf(BasicHeader("Authorization", "Basic ${auth}"))
        return HttpClients.custom().setDefaultHeaders(headers).build()
    }

}
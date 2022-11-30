package at.asitplus.wallet.backend.config

import io.github.aakira.napier.Napier
import org.slf4j.LoggerFactory
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.context.event.EventListener
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.EnumerablePropertySource
import org.springframework.stereotype.Component

@Component
class AppContextEventListener {

    @EventListener
    fun handleContextRefreshed(event: ContextRefreshedEvent) {
        printActiveProperties(event.applicationContext.environment as ConfigurableEnvironment)
    }

    fun printActiveProperties(env: ConfigurableEnvironment) {
        Napier.i("************************* ACTIVE APP PROPERTIES ******************************")
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
                    if (it.contains("password", ignoreCase = true)||it.contains("api-key", ignoreCase = true)) {
                        Napier.i("$it=***")
                    } else {
                        Napier.i("$it=${env.getProperty(it)}")
                    }

                } catch (e: Exception) {
                    Napier.w("$it -> ${e.message}")
                }
            }
        Napier.i("******************************************************************************")
    }
}
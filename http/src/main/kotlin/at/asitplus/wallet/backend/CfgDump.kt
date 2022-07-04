package at.asitplus.wallet.backend

import org.apache.logging.log4j.LogManager
import org.slf4j.LoggerFactory
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.context.event.EventListener
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.EnumerablePropertySource
import org.springframework.stereotype.Component

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
                    if (it.contains("password", ignoreCase = true)||it.contains("api-key", ignoreCase = true)) {
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
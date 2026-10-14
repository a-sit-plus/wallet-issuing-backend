package at.asitplus.wallet.backend.config

import at.asitplus.wallet.lib.etsi.LoteProfile
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding
import org.springframework.core.convert.converter.Converter
import org.springframework.stereotype.Component

/** LoTE profiles are sealed objects rather than enums; make their names available to Spring configuration. */
@Component
@ConfigurationPropertiesBinding
class LoteProfileConverter : Converter<String, LoteProfile> {
    override fun convert(source: String): LoteProfile = LoteProfile.entries.singleOrNull {
        it.toString().equals(source.trim(), ignoreCase = true)
    } ?: throw IllegalArgumentException("Unknown LoTE profile: $source")
}

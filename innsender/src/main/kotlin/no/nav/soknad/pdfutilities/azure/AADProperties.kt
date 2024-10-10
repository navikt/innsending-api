package no.nav.soknad.pdfutilities.azure

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "aad")
data class AADProperties(
    val clientId: String,
    val clientSecret: String,
    val tenant: String? = null
)

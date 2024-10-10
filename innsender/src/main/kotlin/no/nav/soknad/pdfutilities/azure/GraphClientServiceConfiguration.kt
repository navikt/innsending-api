package no.nav.soknad.pdfutilities.azure

import com.azure.identity.ClientSecretCredential
import com.azure.identity.ClientSecretCredentialBuilder
import com.microsoft.graph.serviceclient.GraphServiceClient
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@EnableConfigurationProperties(AADProperties::class)
@Profile("prod | dev")
class GraphClientServiceConfiguration(private val aadProperties: AADProperties) {

	private val MICROSOFT_GRAPH_SCOPE_V2: String = "https://graph.microsoft.com/"

	@Bean
	fun getGraphClient(): GraphServiceClient {
		return GraphServiceClient(getClientCredentials())
	}

	private fun getClientCredentials(): ClientSecretCredential {
		return ClientSecretCredentialBuilder()
			.tenantId(aadProperties.tenant)
			.clientId(aadProperties.clientId)
			.clientSecret(aadProperties.clientSecret)
			.build()
	}

}

package no.nav.soknad.innsending.consumerapis.pdl

import no.nav.soknad.innsending.exceptions.PdlApiException
import org.springframework.core.io.ClassPathResource
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.stream.Collectors

object PdlApiQuery {
	val HENT_PERSON = readGraphQLQueryFromFile("graphql/pdl/hentPersonInfo.graphql")

	private fun readGraphQLQueryFromFile(file: String): String {
		val classPathResource = ClassPathResource(file)
		try {
			BufferedReader(InputStreamReader(classPathResource.inputStream, StandardCharsets.UTF_8))
				.use { reader ->
					return reader.lines().collect(Collectors.joining("\n"))
				}
		} catch (e: IOException) {
			throw PdlApiException("Failed to read graphql-file: $file", e.message ?: "")
		}
	}

}

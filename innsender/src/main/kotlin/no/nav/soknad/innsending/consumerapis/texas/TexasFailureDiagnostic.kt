package no.nav.soknad.innsending.consumerapis.texas

import com.fasterxml.jackson.core.JsonProcessingException
import org.springframework.http.converter.HttpMessageConversionException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.client.UnknownContentTypeException
import tools.jackson.core.JacksonException
import java.io.IOException
import java.util.Collections
import java.util.IdentityHashMap

/** Kopierer bare typer og kodeposisjoner, aldri meldinger, body, headere eller suppressed exceptions. */
internal data class TexasFailureDiagnostic(
	val operation: String,
	val identityProvider: String,
	val target: String,
	val category: Category,
	val httpStatus: Int?,
	val causes: List<Cause>,
	val causeChainTruncated: Boolean,
) {
	enum class Category { HTTP, TRANSPORT_READ, PARSING_CONVERSION, INVALID_ACCESS_TOKEN, UNKNOWN }

	data class Cause(val type: String, val frames: List<String>)

	companion object {
		private const val MAX_CAUSES = 8

		fun from(operation: String, identityProvider: String, target: String, httpStatus: Int?, error: Throwable?): TexasFailureDiagnostic {
			val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
			val chain = mutableListOf<Throwable>()
			var current = error
			while (current != null && chain.size < MAX_CAUSES && seen.add(current)) {
				chain.add(current)
				current = current.cause
			}
			val category = when {
				error == null -> Category.INVALID_ACCESS_TOKEN
				error is RestClientResponseException -> Category.HTTP
				error is ResourceAccessException -> Category.TRANSPORT_READ
				// Jackson kan pakke lesefeil inn i parserfeil. Jackson 2 sine syntaksfeil er også IOException.
				chain.any { it is IOException && it !is JsonProcessingException } -> Category.TRANSPORT_READ
				chain.any { it is HttpMessageConversionException || it is UnknownContentTypeException || it is JsonProcessingException || it is JacksonException } -> Category.PARSING_CONVERSION
				else -> Category.UNKNOWN
			}
			return TexasFailureDiagnostic(
				operation, identityProvider, target, category,
				(error as? RestClientResponseException)?.statusCode?.value() ?: httpStatus,
				chain.map { cause ->
					val frames = cause.stackTrace
					Cause(cause.javaClass.name, (frames.take(8) + frames.filter {
						it.className.startsWith("no.nav.soknad.innsending.")
					}.take(4)).distinct().map { "${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})" })
				},
				causeChainTruncated = current != null,
			)
		}
	}
}

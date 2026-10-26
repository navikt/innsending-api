package no.nav.soknad.innsending.repository.domain.utils

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.soknad.innsending.repository.domain.models.ApplicationOwner
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ApplicationOwnerConverterTest {

	private val mapper = jacksonObjectMapper()
	private val converter = ApplicationOwnerConverter(mapper)

	@Test
	fun `SQL null is the only value that becomes a missing owner`() {
		assertNull(converter.convertToEntityAttribute(null))
		assertNull(converter.convertToDatabaseColumn(null))
	}

	@Test
	fun `person owner round trips without exposing the identifier in toString`() {
		val owner = ApplicationOwner.person("12345678901")
		assertEquals(owner, converter.convertToEntityAttribute(converter.convertToDatabaseColumn(owner)))
		assertEquals("12345678901", owner.id)
		assertEquals("FNR", owner.idType)
		assertFalse(owner.toString().contains("12345678901"))
	}

	@ParameterizedTest
	@ValueSource(strings = [
		"", "not json", "null", "[]", "true", "1", "\"owner\"", "{}",
		"{\"id\":\"12345678901\"}",
		"{\"idType\":\"FNR\"}",
		"{\"id\":null,\"idType\":\"FNR\"}",
		"{\"id\":12345678901,\"idType\":\"FNR\"}",
		"{\"id\":\"12345678901\",\"idType\":1}",
		"{\"id\":\"12345678901\",\"idType\":null}",
		"{\"id\":\"12345678901\",\"idType\":\"FNR\",\"extra\":true}",
		"{\"id\":\"12345678901\",\"idType\":\"FNR\"} null",
		"{\"id\":\"12345678901\",\"idType\":\"FNR\",\"idType\":\"ORGNR\"}",
	])
	fun `malformed or unknown structures remain distinct from an absent owner`(json: String) {
		val owner = assertNotNull(converter.convertToEntityAttribute(json))
		assertNull(owner.id)
		assertNull(owner.idType)
		val stored = converter.convertToDatabaseColumn(owner)
		assertNotNull(stored)
		assertFalse(mapper.readTree(stored).isNull)
		assertNotNull(converter.convertToEntityAttribute(stored))
	}

	@ParameterizedTest
	@ValueSource(strings = ["ORGNR", "UNKNOWN", "fnr", ""])
	fun `unrecognized identifier types can be read without changing the stored value`(type: String) {
		val owner = assertNotNull(converter.convertToEntityAttribute("""{"id":"12345678901","idType":"$type"}"""))
		assertEquals(type, owner.idType)
		assertEquals("12345678901", owner.id)
	}

	@ParameterizedTest
	@ValueSource(strings = ["", "1234567890", "123456789012", "1234567890a", " 12345678901", "１２３４５６７８９０１", "1234567890\n"])
	fun `copying identifiers does not introduce new request validation in release one`(id: String) {
		val owner = ApplicationOwner.person(id)
		assertEquals(id, owner.id)
		assertEquals("FNR", owner.idType)
		assertEquals(owner, converter.convertToEntityAttribute(converter.convertToDatabaseColumn(owner)))
	}

}

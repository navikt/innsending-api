package no.nav.soknad.innsending.validation

import io.mockk.mockk
import jakarta.validation.Validation
import jakarta.validation.ValidatorFactory
import nl.altindag.log.LogCaptor
import no.nav.soknad.innsending.api.FyllutApi
import no.nav.soknad.innsending.model.AttachmentDto
import no.nav.soknad.innsending.model.ApplicationSubmissionResponse
import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.innsending.model.OpplastingsStatusDto
import no.nav.soknad.innsending.model.SkjemaDokumentDto
import no.nav.soknad.innsending.model.SkjemaDto
import no.nav.soknad.innsending.model.SoknadType
import no.nav.soknad.innsending.model.SubmitApplicationRequest
import no.nav.soknad.innsending.model.VedleggDto
import no.nav.soknad.innsending.utils.builders.DokumentSoknadDtoTestBuilder
import no.nav.soknad.innsending.utils.builders.SkjemaDokumentDtoTestBuilder
import no.nav.soknad.innsending.utils.builders.SkjemaDtoTestBuilder
import no.nav.soknad.innsending.utils.builders.VedleggDtoTestBuilder
import org.hibernate.validator.HibernateValidator
import org.hibernate.validator.cfg.defs.SizeDef
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeneratedListValidationTest {

	private lateinit var logCaptor: LogCaptor
	private lateinit var factory: ValidatorFactory

	@BeforeEach
	fun setUp() {
		logCaptor = LogCaptor.forName("org.hibernate.validator")
		factory = Validation.buildDefaultValidatorFactory()
	}

	@AfterEach
	fun tearDown() {
		try {
			factory.close()
			assertTrue(logCaptor.warnLogs.none { it.contains("HV000271") }, "Container-level @Valid warning")
		} finally {
			logCaptor.close()
		}
	}

	@Test
	fun `prefill and application type parameters validate without container warnings`() {
		val api = mockk<FyllutApi>()
		val validator = factory.validator.forExecutables()
		val prefill = FyllutApi::class.java.getMethod("fyllUtPrefillData", List::class.java)
		val applications = FyllutApi::class.java.getMethod("hentSoknaderForSkjemanr", String::class.java, List::class.java)

		assertTrue(validator.validateParameters(api, prefill, arrayOf(listOf("person", "adresser"))).isEmpty())
		assertTrue(validator.validateParameters(api, applications, arrayOf("test", SoknadType.entries)).isEmpty())
		assertTrue(validator.validateParameters(api, applications, arrayOf("test", null)).isEmpty())
		assertEquals(1, validator.validateParameters(api, prefill, arrayOf(null)).size)
	}

	@Test
	fun `multiple attachments and file IDs validate without container warnings`() {
		val attachments = listOf(attachment("A1"), attachment("A2"))
		assertTrue(factory.validator.validate(submission(attachments)).isEmpty())
		assertTrue(factory.validator.validate(submission(emptyList())).isEmpty())
		assertTrue(factory.validator.validate(submission(null)).isEmpty())
		val documents = List(2) { SkjemaDokumentDtoTestBuilder().build() }
		assertTrue(factory.validator.validate(SkjemaDtoTestBuilder().medVedlegg(documents).build()).isEmpty())
		val storedAttachments = List(2) {
			VedleggDtoTestBuilder().build().copy(fileIds = List(2) { UUID.randomUUID() })
		}
		assertTrue(factory.validator.validate(DokumentSoknadDtoTestBuilder(vedleggsListe = storedAttachments).build()).isEmpty())
	}

	@Test
	fun `generated lists cascade on their elements rather than their container`() {
		for ((type, property) in listOf(
			SubmitApplicationRequest::class.java to "attachments",
			ApplicationSubmissionResponse::class.java to "attachments",
			SkjemaDto::class.java to "vedleggsListe",
			DokumentSoknadDto::class.java to "vedleggsListe",
		)) {
			val descriptor = factory.validator.getConstraintsForClass(type).getConstraintsForProperty(property)
			requireNotNull(descriptor)
			assertFalse(descriptor.isCascaded, "$property must not cascade on the list itself")
			assertTrue(descriptor.constrainedContainerElementTypes.single().isCascaded)
		}
		for (type in listOf(AttachmentDto::class.java, VedleggDto::class.java)) {
			val fileIds = factory.validator.getConstraintsForClass(type).getConstraintsForProperty("fileIds")
			assertTrue(fileIds == null || !fileIds.isCascaded)
		}
	}

	@Test
	fun `Bean Validation still reaches invalid attachment and document elements`() {
		// These generated models have no field constraints today. Test-only constraints prove that
		// their generated @Valid annotations actually cascade, without tightening the API contract.
		val configuration = Validation.byProvider(HibernateValidator::class.java).configure()
		val mapping = configuration.createConstraintMapping()
		mapping.type(AttachmentDto::class.java).field("label").constraint(SizeDef().min(1))
		mapping.type(SkjemaDokumentDto::class.java).field("label").constraint(SizeDef().min(1))
		mapping.type(VedleggDto::class.java).field("label").constraint(SizeDef().min(1))
		configuration.addMapping(mapping).buildValidatorFactory().use { constrainedFactory ->
			val validator = constrainedFactory.validator
			val attachments = listOf(attachment("A1"), attachment("A2").copy(label = ""))
			assertEquals(setOf("attachments[1].label"), validator.validate(submission(attachments)).map { it.propertyPath.toString() }.toSet())
			val document = SkjemaDokumentDtoTestBuilder().build()
			val application = SkjemaDtoTestBuilder().medVedlegg(listOf(document, document.copy(label = ""))).build()
			assertEquals(setOf("vedleggsListe[1].label"), validator.validate(application).map { it.propertyPath.toString() }.toSet())
			assertTrue(validator.validate(submission(listOf(attachment("A1"), attachment("A2")))).isEmpty())
			val storedAttachment = VedleggDtoTestBuilder().build()
			val storedApplication = DokumentSoknadDtoTestBuilder(
				vedleggsListe = listOf(storedAttachment, storedAttachment.copy(label = "")),
			).build()
			assertEquals(setOf("vedleggsListe[1].label"), validator.validate(storedApplication).map { it.propertyPath.toString() }.toSet())
		}
	}

	private fun attachment(code: String) = AttachmentDto(
		attachmentCode = code,
		label = "Test attachment",
		uploadStatus = OpplastingsStatusDto.LastetOpp,
		fileIds = List(2) { UUID.randomUUID() },
	)

	private fun submission(attachments: List<AttachmentDto>?) = SubmitApplicationRequest(
		formNumber = "test",
		title = "Test application",
		tema = "BIL",
		language = "nb",
		mainDocument = byteArrayOf(1),
		mainDocumentAlt = byteArrayOf(2),
		attachments = attachments,
	)
}

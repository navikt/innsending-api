package no.nav.soknad.innsending.util.mapping

import no.nav.soknad.innsending.model.Mimetype

fun mapTilMimetype(mimeString: String?): Mimetype? =
	when (mimeString) {
		"application/pdf" -> Mimetype.applicationSlashPdf
		"application/json" -> Mimetype.applicationSlashJson
		"application/jpeg" -> Mimetype.imageSlashJpeg
		"application/png" -> Mimetype.imageSlashPng
		"application/xml" -> Mimetype.applicationSlashXml
		else -> null
	}

fun mapTilDbMimetype(mimetype: Mimetype?): String? =
	when (mimetype) {
		Mimetype.applicationSlashPdf -> "application/pdf"
		Mimetype.applicationSlashJson -> "application/json"
		Mimetype.imageSlashJpeg -> "application/jpeg"
		Mimetype.imageSlashPng -> "application/png"
		Mimetype.applicationSlashXml -> "application/xml"
		else -> null
	}

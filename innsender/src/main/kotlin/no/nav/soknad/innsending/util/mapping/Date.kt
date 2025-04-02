package no.nav.soknad.innsending.util.mapping

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.math.absoluteValue

fun mapTilOffsetDateTime(localDateTime: LocalDateTime?): OffsetDateTime? =
	localDateTime?.atOffset(ZoneId.of("CET").rules.getOffset(localDateTime))

fun mapTilOffsetDateTime(localDateTime: LocalDateTime, offset: Long): OffsetDateTime {
	if (offset < 0) {
		return localDateTime.atOffset(ZoneId.of("CET").rules.getOffset(localDateTime)).minusDays(offset.absoluteValue)
	}
	return localDateTime.atOffset(ZoneId.of("CET").rules.getOffset(localDateTime)).plusDays(offset)
}

fun mapTilLocalDateTime(offsetDateTime: OffsetDateTime?): LocalDateTime? =
	offsetDateTime?.toLocalDateTime()

fun LocalDateTime.toOffsetDateTime(): OffsetDateTime = this.atOffset(ZoneId.of("CET").rules.getOffset(this))

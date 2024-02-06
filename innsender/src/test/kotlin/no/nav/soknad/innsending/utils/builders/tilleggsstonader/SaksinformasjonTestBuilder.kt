package no.nav.soknad.innsending.utils.builders.tilleggsstonader

import no.nav.soknad.innsending.model.Saksinformasjon
import no.nav.soknad.innsending.model.Vedtaksinformasjon

class SaksinformasjonTestBuilder {
	private var saksnummerArena: String = "12837895"
	private var sakstype: String = "TSR"
	private var vedtaksinformasjon: List<Vedtaksinformasjon>? = listOf(VedtaksinformasjonTestBuilder().build())

	fun saksnummerArena(saksnummerArena: String) = apply { this.saksnummerArena = saksnummerArena }
	fun sakstype(sakstype: String) = apply { this.sakstype = sakstype }
	fun vedtaksinformasjon(vedtaksinformasjon: List<Vedtaksinformasjon>?) =
		apply { this.vedtaksinformasjon = vedtaksinformasjon }

	fun build() =
		Saksinformasjon(
			saksnummerArena = saksnummerArena,
			sakstype = sakstype,
			vedtaksinformasjon = vedtaksinformasjon
		)
}

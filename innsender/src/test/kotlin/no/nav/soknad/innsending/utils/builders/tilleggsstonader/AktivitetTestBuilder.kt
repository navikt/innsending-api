package no.nav.soknad.innsending.utils.builders.tilleggsstonader

import no.nav.soknad.innsending.model.Aktivitet
import no.nav.soknad.innsending.model.Maalgruppe
import no.nav.soknad.innsending.model.MaalgruppeType
import no.nav.soknad.innsending.model.Periode
import no.nav.soknad.innsending.model.Saksinformasjon
import java.time.LocalDate

class AktivitetTestBuilder {
	private var aktivitetId: String = "130892484"
	private var aktivitetstype: String = "ARBTREN"
	private var aktivitetsnavn: String = "Arbeidstrening"
	private var periode: Periode = Periode(LocalDate.now().minusMonths(1), LocalDate.now().plusMonths(1))
	private var antallDagerPerUke: Long? = 5
	private var prosentAktivitetsdeltakelse: Long? = 100
	private var aktivitetsstatus: String? = "FULLF"
	private var aktivitetsstatusnavn: String? = "Fullført"
	private var erStoenadsberettigetAktivitet: Boolean? = true
	private var erUtdanningsaktivitet: Boolean? = false
	private var arrangoer: String? = "MOELV BIL & CARAVAN AS"
	private var saksinformasjon: Saksinformasjon? = SaksinformasjonTestBuilder().build()
	private var maalgruppe: Maalgruppe = MaalgruppeTestBuilder().build()

	fun aktivitetId(aktivitetId: String) = apply { this.aktivitetId = aktivitetId }
	fun aktivitetstype(aktivitetstype: String) = apply { this.aktivitetstype = aktivitetstype }
	fun aktivitetsnavn(aktivitetsnavn: String) = apply { this.aktivitetsnavn = aktivitetsnavn }
	fun periode(periode: Periode) = apply { this.periode = periode }
	fun antallDagerPerUke(antallDagerPerUke: Long?) = apply { this.antallDagerPerUke = antallDagerPerUke }
	fun prosentAktivitetsdeltakelse(prosentAktivitetsdeltakelse: Long?) =
		apply { this.prosentAktivitetsdeltakelse = prosentAktivitetsdeltakelse }

	fun aktivitetsstatus(aktivitetsstatus: String?) = apply { this.aktivitetsstatus = aktivitetsstatus }
	fun aktivitetsstatusnavn(aktivitetsstatusnavn: String?) =
		apply { this.aktivitetsstatusnavn = aktivitetsstatusnavn }

	fun erStoenadsberettigetAktivitet(erStoenadsberettigetAktivitet: Boolean?) =
		apply { this.erStoenadsberettigetAktivitet = erStoenadsberettigetAktivitet }

	fun erUtdanningsaktivitet(erUtdanningsaktivitet: Boolean?) =
		apply { this.erUtdanningsaktivitet = erUtdanningsaktivitet }

	fun arrangoer(arrangoer: String?) = apply { this.arrangoer = arrangoer }
	fun saksinformasjon(saksinformasjon: Saksinformasjon?) = apply { this.saksinformasjon = saksinformasjon }
	fun maalgruppe(maalgruppe: MaalgruppeType?) = apply { this.maalgruppe = maalgruppe }

	fun build() = Aktivitet(
		aktivitetId = aktivitetId,
		aktivitetstype = aktivitetstype,
		aktivitetsnavn = aktivitetsnavn,
		periode = periode,
		antallDagerPerUke = antallDagerPerUke,
		prosentAktivitetsdeltakelse = prosentAktivitetsdeltakelse,
		aktivitetsstatus = aktivitetsstatus,
		aktivitetsstatusnavn = aktivitetsstatusnavn,
		erStoenadsberettigetAktivitet = erStoenadsberettigetAktivitet,
		erUtdanningsaktivitet = erUtdanningsaktivitet,
		arrangoer = arrangoer,
		saksinformasjon = saksinformasjon,
		maalgruppe = maalgruppe
	)
}

package no.nav.soknad.innsending.config

import com.google.cloud.storage.Storage
import no.nav.soknad.innsending.service.FilValidatorService
import no.nav.soknad.innsending.service.fillager.FillagerService
import no.nav.soknad.innsending.service.fillager.FillagerServiceMock
import no.nav.soknad.pdfutilities.KonverterTilPdfInterface
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
class FillagerServiceConfig {


	@Bean
	@Profile("!endtoend")
	@Qualifier("fillagerService")
	fun getFillagerService(
		filValidatorService: FilValidatorService,
		konverterTilPdf: KonverterTilPdfInterface,
		cloudStorageConfig: CloudStorageConfig,
		@Qualifier("cloudStorageClient") storage: Storage,
		) = FillagerService( filValidatorService, konverterTilPdf, cloudStorageConfig, storage	)

/*

	@Bean
	@Profile("endtoend")
	@Qualifier("fillagerService")
	fun getFillagerServiceMock(
		filValidatorService: FilValidatorService,
		konverterTilPdf: KonverterTilPdfInterface,
		) =  FillagerServiceMock( filValidatorService, konverterTilPdf)

*/

}

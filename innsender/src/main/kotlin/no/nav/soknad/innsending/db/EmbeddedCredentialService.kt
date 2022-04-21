package no.nav.soknad.innsending.db

/*
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.delay
import no.nav.soknad.innsending.ApplicationState
import org.slf4j.LoggerFactory

class EmbeddedCredentialService : CredentialService {
	private val log = LoggerFactory.getLogger(javaClass)
	private val minRefreshMarginInSeconds = 300L

	private var leaseDuration: Long = 0
	private var renewCredentialsTaskData: RenewCredentialsTaskData? = null

	override suspend fun runRenewCredentialsTask(applicationState: ApplicationState) {
		delay(leaseDuration)
		while (applicationState.ready) {
			log.info("RenewCredentials")
			renewCredentialsTaskData?.run {
				val credentials = getNewCredentials(
					mountPath,
					databaseName,
					role
				)
				dataSource.apply {
					hikariConfigMXBean.setUsername(credentials.username)
					hikariConfigMXBean.setPassword(credentials.password)
					hikariPoolMXBean.softEvictConnections()
				}
			}
			delay(minRefreshMarginInSeconds * 1000)
		}
	}

	override fun getNewCredentials(mountPath: String, databaseName: String, role: Role) =
		Credentials("1234", "postgres", "postgres")

	override fun renewCredentialsTaskData() = renewCredentialsTaskData

	override fun setRenewCredentialsTaskData(
		dataSource: HikariDataSource,
		mountPath: String,
		databaseName: String,
		role: Role
	) {
		renewCredentialsTaskData = RenewCredentialsTaskData(dataSource, mountPath, databaseName, role)
	}
}
*/

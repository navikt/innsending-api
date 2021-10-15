package no.nav.soknad.innsending.db

import com.bettercloud.vault.VaultException
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.delay
import no.nav.soknad.innsending.ApplicationState
import org.slf4j.LoggerFactory

class VaultCredentialService : CredentialService {

	private val log = LoggerFactory.getLogger(javaClass)
	private var renewCredentialsTaskData: RenewCredentialsTaskData? = null
	private var leaseDuration: Long = 0

	override suspend fun runRenewCredentialsTask(applicationState: ApplicationState) {
		delay(leaseDuration)
		while (applicationState.ready) {
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
			delay(Vault.suggestedRefreshIntervalInMillis(leaseDuration * 1000))
		}
	}

	override fun getNewCredentials(mountPath: String, databaseName: String, role: Role): Credentials {
		val path = "$mountPath/creds/$databaseName-$role"
		log.info("Getting database credentials for path '$path'")
		try {
			val response = Vault.client.logical().read(path)
			val username = checkNotNull(response.data["username"]) { "Username is not set in response from Vault" }
			val password = checkNotNull(response.data["password"]) { "Password is not set in response from Vault" }
			log.info("Got new credentials (username=$username, leaseDuration=${response.leaseDuration})")
			leaseDuration = response.leaseDuration
			return Credentials(response.leaseId, username, password)
		} catch (e: VaultException) {
			when (e.httpStatusCode) {
				403 -> log.error("Vault denied permission to fetch database credentials for path '$path'", e)
				else -> log.error("Could not fetch database credentials for path '$path'", e)
			}
			throw e
		}
	}

	override fun renewCredentialsTaskData(): RenewCredentialsTaskData? {
		return renewCredentialsTaskData
	}

	override fun setRenewCredentialsTaskData(
		dataSource: HikariDataSource,
		mountPath: String,
		databaseName: String,
		role: Role
	) {
		renewCredentialsTaskData = RenewCredentialsTaskData(dataSource, mountPath, databaseName, role)
	}
}

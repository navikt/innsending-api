package no.nav.soknad.innsending.util

class Timer private constructor() {
	private var startTime: Long = System.currentTimeMillis()

	companion object {
		fun start(): Timer {
			return Timer()
		}
	}

	fun getElapsedTimeMs(): Long = System.currentTimeMillis() - startTime
}

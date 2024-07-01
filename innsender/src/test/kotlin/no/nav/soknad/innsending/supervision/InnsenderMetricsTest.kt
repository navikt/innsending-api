package no.nav.soknad.innsending.supervision

import io.prometheus.metrics.model.registry.PrometheusRegistry
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.supervision.counters.outgoingrequests.ExternalSystem
import no.nav.soknad.innsending.supervision.counters.outgoingrequests.MethodResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class InnsenderMetricsTest : ApplicationTest() {

	@Autowired
	lateinit var innsenderMetrics: InnsenderMetrics

	@Autowired
	lateinit var registry: PrometheusRegistry

	@BeforeEach
	fun init() {
		innsenderMetrics.unregisterMetrics()
		innsenderMetrics.registerMetrics()
	}

	@Test
	fun testFileNumberOfPages() {
		val numbers = listOf(120.0, 30.0, 50.0)

		for (element in numbers) {
			innsenderMetrics.setFileNumberOfPages(element.toLong())
		}

		val summary = innsenderMetrics.fileNumberOfPagesSummary
		val sum = summary.collect().dataPoints[0].sum
		val count = summary.collect().dataPoints[0].count

		assertEquals(numbers.sum(), sum)
		assertEquals(numbers.size.toLong(), count)

	}

	@Test
	fun testFileSizes() {
		val sizes = listOf(7128.0, 1002232.0, 25550.0, 35550.0)
		for (element in sizes) {
			innsenderMetrics.setFileSize(element.toLong())
		}

		val summary = innsenderMetrics.fileSizeSummary
		val sum = summary.collect().dataPoints[0].sum
		val count = summary.collect().dataPoints[0].count

		assertEquals(sizes.sum(), sum)
		assertEquals(sizes.size.toLong(), count)
	}

	@Test
	fun testFileSizes_and_numberOfPages() {
		val pagesAndSizes = listOf(
			listOf(150.0, 7000.0),
			listOf(30.0, 1000000.0),
			listOf(50.0, 50000.0),
			listOf(10.0, 20000.0),
			listOf(40.0, 30000.0),
			listOf(20.0, 35550.0)
		)
		for (element in pagesAndSizes) {
			innsenderMetrics.setFileNumberOfPages(element[0].toLong())
			innsenderMetrics.setFileSize(element[1].toLong())
		}

		val pagesSummary = innsenderMetrics.fileNumberOfPagesSummary
		val pagesSum = pagesSummary.collect().dataPoints[0].sum
		val pagesCount = pagesSummary.collect().dataPoints[0].count

		val fileSizeSummary = innsenderMetrics.fileSizeSummary
		val fileSizeSum = fileSizeSummary.collect().dataPoints[0].sum
		val fileSizeCount = fileSizeSummary.collect().dataPoints[0].count

		assertEquals(pagesAndSizes.sumOf { it[0] }, pagesSum)
		assertEquals(pagesAndSizes.size.toLong(), pagesCount)

		assertEquals(pagesAndSizes.sumOf { it[1] }, fileSizeSum)
		assertEquals(pagesAndSizes.size.toLong(), fileSizeCount)
	}

	@Test
	fun `Should initialize all possible label combinations for outgoing requests counter with zero`() {
		val counter = innsenderMetrics.outgoingRequestsCounter.instance
		val dataPoints = counter.collect().dataPoints
		val totalNumberOfMethods = ExternalSystem.entries.fold(0) { acc, system -> acc + system.methods.size }
		val numberOfMethodResults = MethodResult.entries.size

		assertEquals(totalNumberOfMethods * numberOfMethodResults, dataPoints.size)
		dataPoints.forEach { assertEquals(0.0, it.value, "Value should be 0.0 for labels ${it.labels}") }
	}

	@Test
	fun `Should ignore unknown method for outgoing requests counter`() {
		innsenderMetrics.outgoingRequestsCounter.inc(ExternalSystem.ARENA, "invalidMethod")

		val counter = innsenderMetrics.outgoingRequestsCounter.instance
		val dataPoints = counter.collect().dataPoints
		dataPoints.forEach { assertEquals(0.0, it.value, "Value should be 0.0 for labels ${it.labels}") }
	}

	@Test
	fun `Should register outgoing request with result ok by default`() {
		innsenderMetrics.outgoingRequestsCounter.inc(ExternalSystem.ARENA, "get_maalgrupper")

		val counter = innsenderMetrics.outgoingRequestsCounter.instance
		val dataPoints = counter.collect().dataPoints
		val dataPoint = dataPoints.find {
			it.labels.get("method")?.equals("get_maalgrupper") == true
				&& it.labels.get("result")?.equals(MethodResult.CODE_OK.code) == true
		}
		assertEquals(1.0, dataPoint?.value)
	}

}

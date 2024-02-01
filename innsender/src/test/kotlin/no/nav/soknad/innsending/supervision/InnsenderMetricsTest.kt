package no.nav.soknad.innsending.supervision

import no.nav.soknad.innsending.ApplicationTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class InnsenderMetricsTest : ApplicationTest() {

	@Autowired
	lateinit var innsenderMetrics: InnsenderMetrics

	@Test
	fun testFileNumberOfPages() {
		innsenderMetrics.fileNumberOfPagesClear()
		val numbers = listOf(120.0, 30.0, 50.0)
		for (element in numbers) {
			innsenderMetrics.fileNumberOfPagesSet(element.toLong())
		}

		val fileNumberOfPagesMetrics = innsenderMetrics.fileNumberOfPagesGet()

		Assertions.assertTrue(fileNumberOfPagesMetrics != null)

		Assertions.assertEquals(numbers.max(), fileNumberOfPagesMetrics.quantiles?.get(0.95))
		Assertions.assertEquals(numbers.sum() / numbers.size, fileNumberOfPagesMetrics.sum / fileNumberOfPagesMetrics.count)

	}

	@Test
	fun testFileSizes() {
		innsenderMetrics.fileSizeClear()
		val sizes = listOf(7128.0, 1002232.0, 25550.0, 35550.0)
		for (element in sizes) {
			innsenderMetrics.fileSizeSet(element.toLong())
		}
		val fileSizeMetrics = innsenderMetrics.fileSizeGet()

		Assertions.assertTrue(fileSizeMetrics != null)
		Assertions.assertEquals(sizes.max(), fileSizeMetrics.quantiles?.get(0.95))
		Assertions.assertEquals(sizes.sum() / sizes.size, fileSizeMetrics.sum / fileSizeMetrics.count)

	}

	@Test
	fun testFileSizes_and_numberOfPages() {
		innsenderMetrics.fileNumberOfPagesClear()
		innsenderMetrics.fileSizeClear()
		val pagesAndSizes = listOf(
			listOf(150.0, 7000.0),
			listOf(30.0, 1000000.0),
			listOf(50.0, 50000.0),
			listOf(10.0, 20000.0),
			listOf(40.0, 30000.0),
			listOf(20.0, 35550.0)
		)
		for (element in pagesAndSizes) {
			innsenderMetrics.fileNumberOfPagesSet(element.get(0).toLong())
			innsenderMetrics.fileSizeSet(element.get(1).toLong())
		}

		val fileNumberOfPagesMetrics = innsenderMetrics.fileNumberOfPagesGet()
		val fileSizeMetrics = innsenderMetrics.fileSizeGet()

		Assertions.assertTrue(fileNumberOfPagesMetrics != null)
		Assertions.assertEquals(pagesAndSizes.map { it.get(0) }.max(), fileNumberOfPagesMetrics.quantiles?.get(0.95))
		Assertions.assertEquals(
			pagesAndSizes.map { it.get(0) }.sum() / pagesAndSizes.map { it.get(0) }.size,
			fileNumberOfPagesMetrics.sum / fileNumberOfPagesMetrics.count
		)

		Assertions.assertTrue(fileSizeMetrics != null)
		Assertions.assertEquals(pagesAndSizes.map { it.get(1) }.max(), fileSizeMetrics.quantiles?.get(0.95))
		Assertions.assertEquals(
			pagesAndSizes.map { it.get(1) }.sum() / pagesAndSizes.map { it.get(1) }.size,
			fileSizeMetrics.sum / fileSizeMetrics.count
		)

	}

}

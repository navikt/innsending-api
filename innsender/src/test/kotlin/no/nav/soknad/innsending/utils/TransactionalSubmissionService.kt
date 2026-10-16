package no.nav.soknad.innsending.utils

import no.nav.soknad.innsending.service.InnsendingService
import org.springframework.aop.framework.ProxyFactory
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource
import org.springframework.transaction.interceptor.TransactionInterceptor

// Apply production transaction boundaries to manually constructed services, keeping archival synchronous.
fun transactionalSubmissionService(
	service: InnsendingService,
	transactionManager: PlatformTransactionManager,
): InnsendingService = ProxyFactory(service).apply {
	isProxyTargetClass = true
	addAdvice(TransactionInterceptor(transactionManager, AnnotationTransactionAttributeSource()))
}.proxy as InnsendingService

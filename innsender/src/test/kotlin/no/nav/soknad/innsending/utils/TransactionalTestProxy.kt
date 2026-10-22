package no.nav.soknad.innsending.utils

import org.springframework.aop.framework.ProxyFactory
import org.springframework.transaction.TransactionManager
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource
import org.springframework.transaction.interceptor.TransactionInterceptor

/**
 * Applies only transaction advice to a manually constructed, class-proxyable test service.
 * Transactional methods must be public and overridable; self-invocation bypasses the proxy.
 */
inline fun <reified T : Any> withTransactions(
	service: T,
	transactionManager: TransactionManager,
): T {
	val interceptor = TransactionInterceptor().apply {
		setTransactionManager(transactionManager)
		setTransactionAttributeSource(AnnotationTransactionAttributeSource())
	}
	val proxy = ProxyFactory(service).apply {
		isProxyTargetClass = true
		addAdvice(interceptor)
	}.proxy
	return proxy as? T
		?: error("Transaction proxy is not assignable to ${T::class.qualifiedName}")
}

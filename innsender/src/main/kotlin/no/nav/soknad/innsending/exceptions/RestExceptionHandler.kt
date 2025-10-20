package no.nav.soknad.innsending.exceptions

import com.google.cloud.storage.StorageException
import jakarta.servlet.http.HttpServletRequest
import no.nav.security.token.support.core.exceptions.JwtTokenMissingException
import no.nav.security.token.support.spring.validation.interceptor.JwtTokenUnauthorizedException
import no.nav.soknad.innsending.exceptions.utils.messageForLog
import no.nav.soknad.innsending.model.RestErrorResponseDto
import no.nav.soknad.innsending.service.config.annotation.ConfigVerificationException
import org.apache.catalina.connector.ClientAbortException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.multipart.MultipartException
import org.springframework.web.servlet.resource.NoResourceFoundException
import java.nio.charset.IllegalCharsetNameException
import java.nio.charset.MalformedInputException
import java.nio.charset.UnmappableCharacterException
import java.nio.charset.UnsupportedCharsetException
import java.time.OffsetDateTime


@ControllerAdvice
class RestExceptionHandler {

	val logger: Logger = LoggerFactory.getLogger(javaClass)

	// 404
	@ExceptionHandler
	fun resourceNotFoundException(exception: ResourceNotFoundException): ResponseEntity<RestErrorResponseDto> {
		logger.warn(exception.messageForLog, exception)
		return ResponseEntity(
			RestErrorResponseDto(
				message = exception.message,
				timestamp = OffsetDateTime.now(),
				errorCode = exception.errorCode.code
			), HttpStatus.NOT_FOUND
		)
	}

	@ExceptionHandler
	fun noResourceFoundException(exception: NoResourceFoundException): ResponseEntity<RestErrorResponseDto> {
		logger.warn(exception.messageForLog, exception)
		return ResponseEntity(
			RestErrorResponseDto(
				message = exception.message,
				timestamp = OffsetDateTime.now(),
				errorCode = ErrorCode.NOT_FOUND.code
			), HttpStatus.NOT_FOUND
		)
	}

	@ExceptionHandler(
		value = [
			UnsupportedCharsetException::class,
			MalformedInputException::class,
			UnmappableCharacterException::class,
			CharacterCodingException::class,
			IllegalCharsetNameException::class
		]
	)
	fun charsetExceptions(exception: Exception): ResponseEntity<RestErrorResponseDto> {
		logger.warn(exception.messageForLog, exception)
		return ResponseEntity(
			RestErrorResponseDto(
				message = "${exception::class.simpleName} - ${exception.message}",
				timestamp = OffsetDateTime.now(),
				errorCode = ErrorCode.GENERAL_ERROR.code
			), HttpStatus.BAD_REQUEST
		)
	}

	// 500
	@ExceptionHandler
	fun backendErrorException(exception: BackendErrorException): ResponseEntity<RestErrorResponseDto> {
		logger.error(exception.messageForLog, exception)
		return ResponseEntity(
			RestErrorResponseDto(
				message = exception.message,
				timestamp = OffsetDateTime.now(),
				errorCode = exception.errorCode.code
			), HttpStatus.INTERNAL_SERVER_ERROR
		)
	}

	// 400
	@ExceptionHandler
	fun illegalActionException(exception: IllegalActionException): ResponseEntity<RestErrorResponseDto> {
		logger.warn(exception.messageForLog, exception)
		return ResponseEntity(
			RestErrorResponseDto(
				message = exception.message,
				timestamp = OffsetDateTime.now(),
				errorCode = exception.errorCode.code
			), HttpStatus.BAD_REQUEST
		)
	}

	// 400
	@ExceptionHandler
	fun illegalArgumentException(exception: IllegalArgumentException): ResponseEntity<RestErrorResponseDto> {
		logger.warn(exception.message, exception)
		return ResponseEntity(
			RestErrorResponseDto(
				message = exception.message,
				timestamp = OffsetDateTime.now(),
				errorCode = ErrorCode.ILLEGAL_ARGUMENT.code
			), HttpStatus.BAD_REQUEST
		)
	}

	// 401
	@ExceptionHandler
	fun unauthenticatedHandler(
		request: HttpServletRequest,
		exception: JwtTokenMissingException
	): ResponseEntity<RestErrorResponseDto?>? {
		logger.warn("Autentisering feilet ved kall til " + request.requestURI + ": " + exception.messageForLog, exception)

		return ResponseEntity(
			RestErrorResponseDto(
				message = "Autentisering feilet",
				timestamp = OffsetDateTime.now(),
				errorCode = "errorCode.unauthorized"
			), HttpStatus.UNAUTHORIZED
		)
	}

	// 403
	@ExceptionHandler
	fun unauthorizedExceptionHandler(
		request: HttpServletRequest,
		exception: JwtTokenUnauthorizedException
	): ResponseEntity<RestErrorResponseDto?>? {
		logger.warn("Autorisering feilet ved kall til " + request.requestURI + ": " + exception.message, exception)

		return ResponseEntity(
			RestErrorResponseDto(
				message = "Autorisering feilet",
				timestamp = OffsetDateTime.now(),
				errorCode = "errorCode.forbidden"
			), HttpStatus.FORBIDDEN
		)
	}

	@ExceptionHandler
	fun handleConfigVerificationException(
		request: HttpServletRequest,
		exception: ConfigVerificationException,
	): ResponseEntity<RestErrorResponseDto?>? {
		logger.warn("Kall til ${request.requestURI} avvist (${exception.configuration.key}): ${exception.message}", exception)

		return ResponseEntity(
			RestErrorResponseDto(
				message = exception.message,
				timestamp = OffsetDateTime.now(),
				errorCode = exception.errorCode.code
			), exception.httpStatus
		)
	}

	@ExceptionHandler(value = [UnsupportedOperationException::class])
	fun unsupportedOperationException(
		request: HttpServletRequest,
		exception: UnsupportedOperationException
	): ResponseEntity<RestErrorResponseDto?>? {
		logger.warn("Kall til ${request.requestURI} ikke støttet: ${exception.messageForLog}", exception)

		return ResponseEntity(
			RestErrorResponseDto(
				message = "Operasjonen er ikke støttet",
				timestamp = OffsetDateTime.now(),
				errorCode = "errorCode.unsupportedOperation"
			), HttpStatus.NOT_IMPLEMENTED
		)
	}

	@ExceptionHandler(value = [StorageException::class])
	fun storageExceptionHandler(
		request: HttpServletRequest,
		exception: StorageException
	): ResponseEntity<RestErrorResponseDto?>? {
		logger.error("Feil ved kall til ${request.requestURI}: ${exception.message}", exception)

		return ResponseEntity(
			RestErrorResponseDto(
				message = "Feil under interaksjon med lagringstjenesten for filer",
				timestamp = OffsetDateTime.now(),
				errorCode = "errorCode.storageError"
			), HttpStatus.INTERNAL_SERVER_ERROR
		)
	}

	// 500
	@ExceptionHandler
	fun generalException(exception: Exception): ResponseEntity<RestErrorResponseDto> {
		logger.error(exception.messageForLog, exception)
		return ResponseEntity(
			RestErrorResponseDto(
				message = exception.message ?: "Noe gikk galt, prøv igjen senere",
				timestamp = OffsetDateTime.now(),
				errorCode = ErrorCode.GENERAL_ERROR.code,
			), HttpStatus.INTERNAL_SERVER_ERROR
		)
	}

	// If client aborts we don't want to log this as an error
	@ExceptionHandler
	fun clientAbortException(exception: ClientAbortException): ResponseEntity<RestErrorResponseDto> {
		logger.warn(exception.messageForLog, exception)
		return ResponseEntity(
			RestErrorResponseDto(
				message = exception.message ?: "Noe gikk galt, prøv igjen senere",
				timestamp = OffsetDateTime.now(),
				errorCode = ErrorCode.GENERAL_ERROR.code,
			), HttpStatus.INTERNAL_SERVER_ERROR
		)
	}

	// When the client aborts there is a multipart exception caused by ClientAbortException. We don't want to log this as an error.
	// Causes could be that the user closes the browser, loses internet connection or that the upload times out.
	@ExceptionHandler
	fun multipartException(exception: MultipartException): ResponseEntity<RestErrorResponseDto> {
		logger.warn(exception.messageForLog, exception)

		return ResponseEntity(
			RestErrorResponseDto(
				message = exception.message ?: "Noe gikk galt, prøv igjen senere",
				timestamp = OffsetDateTime.now(),
				errorCode = ErrorCode.GENERAL_ERROR.code,
			), HttpStatus.INTERNAL_SERVER_ERROR
		)
	}
}

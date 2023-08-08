package no.nav.soknad.innsending.exceptions

import jakarta.servlet.http.HttpServletRequest
import no.nav.security.token.support.core.exceptions.JwtTokenMissingException
import no.nav.security.token.support.spring.validation.interceptor.JwtTokenUnauthorizedException
import no.nav.soknad.innsending.dto.RestErrorResponseDto
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import java.time.LocalDateTime


@ControllerAdvice
class RestExceptionHandler {

	val logger: Logger = LoggerFactory.getLogger(javaClass)

	@ExceptionHandler
	fun resourceNotFoundException(exception: ResourceNotFoundException): ResponseEntity<RestErrorResponseDto> {
		logger.warn(exception.message, exception)
		return ResponseEntity(
			RestErrorResponseDto(
				exception.arsak ?: "",
				exception.message ?: "",
				LocalDateTime.now(),
				exception.errorCode
			), HttpStatus.NOT_FOUND
		)
	}

	@ExceptionHandler
	fun backendErrorException(exception: BackendErrorException): ResponseEntity<RestErrorResponseDto> {
		logger.error(exception.message, exception)
		return ResponseEntity(
			RestErrorResponseDto(
				exception.arsak ?: "",
				exception.message ?: "",
				LocalDateTime.now(),
				exception.errorCode
			), HttpStatus.INTERNAL_SERVER_ERROR
		)
	}

	@ExceptionHandler
	fun safApiErrorException(exception: SafApiException): ResponseEntity<RestErrorResponseDto> {
		logger.error(exception.message, exception)
		return ResponseEntity(
			RestErrorResponseDto(
				exception.message ?: "",
				exception.message ?: "",
				LocalDateTime.now(),
				exception.errorCode
			), HttpStatus.INTERNAL_SERVER_ERROR
		)
	}

	@ExceptionHandler
	fun sanityException(exception: SanityException): ResponseEntity<RestErrorResponseDto> {
		logger.error(exception.message, exception)
		return ResponseEntity(
			RestErrorResponseDto(
				exception.message ?: "",
				exception.message ?: "",
				LocalDateTime.now(),
				exception.errorCode
			), HttpStatus.INTERNAL_SERVER_ERROR
		)
	}

	@ExceptionHandler
	fun illegalActionException(exception: IllegalActionException): ResponseEntity<RestErrorResponseDto> {
		logger.warn(exception.message, exception)
		return ResponseEntity(
			RestErrorResponseDto(
				exception.arsak ?: "",
				exception.message ?: "",
				LocalDateTime.now(),
				exception.errorCode
			), HttpStatus.METHOD_NOT_ALLOWED
		)
	}

	@ExceptionHandler(value = [JwtTokenMissingException::class, JwtTokenUnauthorizedException::class])
	fun unauthorizedExceptionHandler(
		request: HttpServletRequest,
		exception: Exception
	): ResponseEntity<RestErrorResponseDto?>? {
		logger.warn("Autentisering feilet ved kall til " + request.requestURI + ": " + exception.message, exception)

		return ResponseEntity(
			RestErrorResponseDto(
				arsak = exception.message ?: "",
				message = "Autentisering feilet",
				timeStamp = LocalDateTime.now(),
				errorCode = "errorCode.unauthorized"
			), HttpStatus.UNAUTHORIZED
		)
	}

	@ExceptionHandler
	fun generalException(exception: Exception): ResponseEntity<RestErrorResponseDto> {
		logger.error(exception.message, exception)
		return ResponseEntity(
			RestErrorResponseDto(
				exception.message ?: "",
				"Noe gikk galt, prøv igjen senere",
				LocalDateTime.now(),
				"errorCode.somethingFailedTryLater"
			), HttpStatus.INTERNAL_SERVER_ERROR
		)
	}
}

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
				exception.message,
				LocalDateTime.now(),
				exception.errorCode.code
			), HttpStatus.NOT_FOUND
		)
	}

	// 500
	@ExceptionHandler
	fun backendErrorException(exception: BackendErrorException): ResponseEntity<RestErrorResponseDto> {
		logger.error(exception.message, exception)
		return ResponseEntity(
			RestErrorResponseDto(
				exception.message,
				LocalDateTime.now(),
				exception.errorCode.code
			), HttpStatus.INTERNAL_SERVER_ERROR
		)
	}

	// 400
	@ExceptionHandler
	fun illegalActionException(exception: IllegalActionException): ResponseEntity<RestErrorResponseDto> {
		logger.warn(exception.message, exception)
		return ResponseEntity(
			RestErrorResponseDto(
				exception.message,
				LocalDateTime.now(),
				exception.errorCode.code
			), HttpStatus.BAD_REQUEST
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
				exception.message ?: "Noe gikk galt, prøv igjen senere",
				LocalDateTime.now(),
				ErrorCode.GENERAL_ERROR.code,
			), HttpStatus.INTERNAL_SERVER_ERROR
		)
	}
}

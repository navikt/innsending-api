package no.nav.soknad.innsending.exceptions

import no.nav.soknad.innsending.dto.RestErrorResponseDto
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import java.time.LocalDateTime


@ControllerAdvice
class RestExceptionHandler {

	@ExceptionHandler
	fun resourceNotFoundException(exception: ResourceNotFoundException): ResponseEntity<RestErrorResponseDto> {
		return ResponseEntity(RestErrorResponseDto(exception.arsak ?: "", exception.message ?: "", LocalDateTime.now()), HttpStatus.NOT_FOUND)
	}

	@ExceptionHandler
	fun backendErrorException(exception: BackendErrorException): ResponseEntity<RestErrorResponseDto> {
		return ResponseEntity(RestErrorResponseDto(exception.message ?: "", exception.message ?: "", LocalDateTime.now()), HttpStatus.INTERNAL_SERVER_ERROR)
	}

	@ExceptionHandler
	fun illegalActionException(exception: IllegalActionException): ResponseEntity<RestErrorResponseDto> {
		return ResponseEntity(RestErrorResponseDto(exception.arsak ?: "", exception.message ?: "", LocalDateTime.now()), HttpStatus.METHOD_NOT_ALLOWED)
	}

	@ExceptionHandler
	fun generalException(exception: Exception): ResponseEntity<RestErrorResponseDto> {
		return ResponseEntity(RestErrorResponseDto(exception.message ?: "", "Noe gikk galt, prøv igjen senere", LocalDateTime.now()), HttpStatus.METHOD_NOT_ALLOWED)
	}
}

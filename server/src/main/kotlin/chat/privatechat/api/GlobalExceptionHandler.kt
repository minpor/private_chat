package chat.privatechat.api

import chat.privatechat.application.ChatAccessDeniedException
import chat.privatechat.application.ChatNotFoundException
import chat.privatechat.application.DraftNotFoundException
import chat.privatechat.application.DraftRevisionConflictException
import chat.privatechat.application.InvalidCredentialsException
import chat.privatechat.application.InvalidRefreshTokenException
import chat.privatechat.application.MessageNotFoundException
import chat.privatechat.application.RateLimitExceededException
import chat.privatechat.application.UserNotFoundException
import chat.privatechat.application.UsernameTakenException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
@Suppress("TooManyFunctions")
class GlobalExceptionHandler {

    @ExceptionHandler(InvalidCredentialsException::class)
    fun invalidCredentials(ex: InvalidCredentialsException): ResponseEntity<ProblemDetail> =
        problem(HttpStatus.UNAUTHORIZED, ex.message ?: "Invalid credentials")

    @ExceptionHandler(InvalidRefreshTokenException::class)
    fun invalidRefresh(ex: InvalidRefreshTokenException): ResponseEntity<ProblemDetail> =
        problem(HttpStatus.UNAUTHORIZED, ex.message ?: "Invalid refresh token")

    @ExceptionHandler(ChatAccessDeniedException::class, AccessDeniedException::class)
    fun accessDenied(ex: RuntimeException): ResponseEntity<ProblemDetail> =
        problem(HttpStatus.FORBIDDEN, ex.message ?: "Access denied")

    @ExceptionHandler(
        ChatNotFoundException::class,
        MessageNotFoundException::class,
        UserNotFoundException::class
    )
    fun notFound(ex: RuntimeException): ResponseEntity<ProblemDetail> =
        problem(HttpStatus.NOT_FOUND, ex.message ?: "Not found")

    @ExceptionHandler(DraftNotFoundException::class)
    fun draftGone(ex: DraftNotFoundException): ResponseEntity<ProblemDetail> =
        problem(HttpStatus.GONE, ex.message ?: "Draft not found")

    @ExceptionHandler(DraftRevisionConflictException::class)
    fun draftRevisionConflict(ex: DraftRevisionConflictException): ResponseEntity<ProblemDetail> =
        problem(HttpStatus.CONFLICT, ex.message ?: "Draft revision conflict")

    @ExceptionHandler(RateLimitExceededException::class)
    fun rateLimit(ex: RateLimitExceededException): ResponseEntity<ProblemDetail> {
        val body = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.message ?: "Rate limit exceeded")
        body.setProperty("retryAfterSeconds", ex.retryAfterSeconds)
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header("Retry-After", ex.retryAfterSeconds.toString())
            .body(body)
    }

    @ExceptionHandler(AuthenticationException::class)
    fun unauthorized(@Suppress("UNUSED_PARAMETER") ex: AuthenticationException): ResponseEntity<ProblemDetail> =
        problem(HttpStatus.UNAUTHORIZED, "Unauthorized")

    @ExceptionHandler(IllegalArgumentException::class)
    fun badRequest(ex: IllegalArgumentException): ResponseEntity<ProblemDetail> =
        problem(HttpStatus.BAD_REQUEST, ex.message ?: "Bad request")

    @ExceptionHandler(IllegalStateException::class, UsernameTakenException::class)
    fun conflict(ex: RuntimeException): ResponseEntity<ProblemDetail> =
        problem(HttpStatus.CONFLICT, ex.message ?: "Conflict")

    private fun problem(status: HttpStatus, detail: String): ResponseEntity<ProblemDetail> {
        val body = ProblemDetail.forStatusAndDetail(status, detail)
        return ResponseEntity.status(status).body(body)
    }
}

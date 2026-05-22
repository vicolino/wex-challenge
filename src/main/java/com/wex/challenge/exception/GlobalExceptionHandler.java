package com.wex.challenge.exception;

import jakarta.validation.ConstraintViolationException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(PurchaseTransactionNotFoundException.class)
    public ProblemDetail handleNotFound(PurchaseTransactionNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Purchase transaction not found", ex.getMessage());
    }

    @ExceptionHandler(ExchangeRateNotAvailableException.class)
    public ProblemDetail handleRateUnavailable(ExchangeRateNotAvailableException ex) {
        ProblemDetail pd = problem(HttpStatus.UNPROCESSABLE_CONTENT,
                "Exchange rate unavailable", ex.getMessage());
        pd.setProperty("targetCurrency", ex.getCurrency());
        pd.setProperty("purchaseDate", ex.getPurchaseDate().toString());
        return pd;
    }

    @ExceptionHandler(TreasuryApiException.class)
    public ProblemDetail handleTreasuryFailure(TreasuryApiException ex) {
        log.error("Treasury API call failed", ex);
        return problem(HttpStatus.BAD_GATEWAY,
                "Treasury Reporting Rates of Exchange API unavailable", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleBeanValidation(MethodArgumentNotValidException ex) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of(
                        "field", fe.getField(),
                        "message", fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage()))
                .toList();
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Validation failed", "Request body is invalid");
        pd.setProperty("errors", errors);
        return pd;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        List<Map<String, String>> errors = ex.getConstraintViolations().stream()
                .map(v -> Map.of(
                        "field", v.getPropertyPath().toString(),
                        "message", v.getMessage()))
                .toList();
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Validation failed", "Request parameters are invalid");
        pd.setProperty("errors", errors);
        return pd;
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ProblemDetail handleBadRequest(Exception ex) {
        return problem(HttpStatus.BAD_REQUEST, "Bad request", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal server error", "An unexpected error occurred");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        pd.setProperty("timestamp", OffsetDateTime.now().toString());
        return pd;
    }
}

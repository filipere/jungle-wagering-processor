package com.junglegaming.wagering.exception;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

// Pega as exceções de toda a aplicação e transforma em resposta HTTP.
// Cada situação tem um status diferente, assim o provedor sabe se pode reenviar ou não.
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 400: dados inválidos (valor com 3 casas, JSON quebrado...)
    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, Object>> handleBadRequest(Exception ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // 400: campos que não passaram nas validações do DTO (@NotBlank, @NotNull...)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        // Monta um mapa campo -> mensagem de erro
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage()));

        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", 400);
        body.put("error", "Validation Error");
        body.put("messages", errors);
        return ResponseEntity.badRequest().body(body);
    }

    // 404: carteira ou transação não encontrada
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    // 409: mesma chave com payload diferente
    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(IdempotencyConflictException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    // 409: violou uma constraint do banco (ex.: carteira duplicada do mesmo jogador e moeda)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleIntegrity(DataIntegrityViolationException ex) {
        return buildResponse(HttpStatus.CONFLICT, "Registro duplicado.");
    }

    // 503: banco fora do ar. É falha temporária, o provedor pode tentar de novo.
    @ExceptionHandler(DataAccessResourceFailureException.class)
    public ResponseEntity<Map<String, Object>> handleDatabaseDown(DataAccessResourceFailureException ex) {
        return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, "Serviço indisponível, tente novamente.");
    }

    // Monta o corpo padrão do erro, para todas as respostas terem o mesmo formato
    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
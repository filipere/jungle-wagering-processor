package com.junglegaming.wagering.exception;

// Lançada quando o mesmo referenceId é usado com dados diferentes.
// Não é uma repetição, é um conflito. Vira HTTP 409.
public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String message) {
        super(message);
    }
}
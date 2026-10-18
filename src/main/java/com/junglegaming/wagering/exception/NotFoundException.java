package com.junglegaming.wagering.exception;

// Lançada quando a carteira ou a transação não existe. Vira HTTP 404.
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
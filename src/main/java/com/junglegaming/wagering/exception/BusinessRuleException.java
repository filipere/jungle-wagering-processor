package com.junglegaming.wagering.exception;

import com.junglegaming.wagering.domain.FailureCode;

// Lançada quando uma regra de negócio é quebrada (ex.: saldo insuficiente).
// Não é erro do sistema: o WageringService captura e salva a transação como REJECTED
// com o código do motivo.
public class BusinessRuleException extends RuntimeException {

    private final FailureCode failureCode;

    public BusinessRuleException(FailureCode failureCode, String message) {
        super(message);
        this.failureCode = failureCode;
    }

    public FailureCode getFailureCode() {
        return failureCode;
    }
}

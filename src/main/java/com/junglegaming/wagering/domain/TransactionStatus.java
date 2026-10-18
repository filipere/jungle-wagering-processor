package com.junglegaming.wagering.domain;

// Em que etapa a transação está.
// PROCESSED e REJECTED são finais: depois que chegam neles, a transação não muda mais.
public enum TransactionStatus {
    PENDING,            // acabou de chegar, ainda não foi aplicada
    PENDING_REFERENCE,  // esperando a transação original chegar (REFUND/ROLLBACK)
    PROCESSED,          // aplicada com sucesso
    REJECTED            // quebrou uma regra de negócio (ex.: sem saldo)
}
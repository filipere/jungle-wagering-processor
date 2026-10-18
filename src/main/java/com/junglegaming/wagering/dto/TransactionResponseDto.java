package com.junglegaming.wagering.dto;

import com.junglegaming.wagering.domain.FailureCode;
import com.junglegaming.wagering.domain.Transaction;
import com.junglegaming.wagering.domain.TransactionStatus;
import java.math.BigDecimal;

// O que a API devolve depois de processar uma transação
public record TransactionResponseDto(
        String transactionId,
        TransactionStatus status,
        FailureCode failureCode,    // só tem valor se foi rejeitada
        BigDecimal balance,         // saldo depois da operação
        boolean idempotentReplay    // true se era uma requisição repetida
) {
    // Monta o DTO a partir da entidade, para não expor a entidade direto na API
    public static TransactionResponseDto from(Transaction tx, boolean replay) {
        return new TransactionResponseDto(tx.getId(), tx.getStatus(), tx.getFailureCode(),
                tx.getBalanceAfter(), replay);
    }
}
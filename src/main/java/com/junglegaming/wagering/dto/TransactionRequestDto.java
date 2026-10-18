package com.junglegaming.wagering.dto;

import com.junglegaming.wagering.domain.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

// O que o provedor envia em POST /api/wagering/transactions.
// É um record porque só carrega dados. As anotações validam o JSON antes de chegar no service.
public record TransactionRequestDto(
        @NotBlank(message = "O ID da carteira é obrigatório.")
        String walletId,

        @NotNull(message = "O tipo de transação é obrigatório.")
        TransactionType type,

        @NotNull(message = "O montante é obrigatório.")
        @DecimalMin(value = "0.01", message = "O montante deve ser maior que zero.")
        BigDecimal amount,

        @NotBlank(message = "A moeda é obrigatória.")
        String currency,

        // Funciona como chave de idempotência
        @NotBlank(message = "O ID de referência (idempotência) é obrigatório.")
        String referenceId,

        // só para REFUND e ROLLBACK: referenceId da transação original
        String relatedReferenceId
) {}
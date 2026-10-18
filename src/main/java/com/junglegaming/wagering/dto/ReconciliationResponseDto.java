package com.junglegaming.wagering.dto;

// Resultado da reconciliação: compara o saldo guardado com o saldo calculado pelo ledger
public record ReconciliationResponseDto(
        String walletId,
        String storedBalance,      // saldo que está na carteira
        String calculatedBalance,  // saldo calculado somando o ledger
        String difference,         // diferença entre os dois
        boolean consistent,        // true se a diferença é zero
        int checkedEntries         // quantos lançamentos do ledger foram somados
) {}
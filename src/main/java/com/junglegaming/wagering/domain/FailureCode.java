package com.junglegaming.wagering.domain;

// Motivo da rejeição de uma transação. É um código fixo para o provedor
// conseguir decidir sozinho o que fazer (reenviar, corrigir ou desistir).
public enum FailureCode {
    INSUFFICIENT_FUNDS,           // BET sem saldo suficiente
    ROLLBACK_INSUFFICIENT_FUNDS,  // a reversão deixaria o saldo negativo
    CURRENCY_MISMATCH,            // moeda diferente da moeda da carteira
    INVALID_REFERENCE,            // referência de outra carteira, não processada ou de tipo errado
    AMOUNT_MISMATCH,              // valor diferente do valor da transação original
    ALREADY_REVERSED,             // a original já foi revertida antes
    REFERENCE_NOT_FOUND           // a original não chegou depois de todas as tentativas
}
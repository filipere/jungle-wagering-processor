package com.junglegaming.wagering.domain;

// Tipos de transação que o sistema conhece
public enum TransactionType {
    OPENING,  // crédito inicial da carteira. É interna, o provedor não pode enviar
    BET,      // aposta: tira dinheiro do saldo
    WIN,      // prêmio: coloca dinheiro no saldo
    LOSS,     // perda: só registra o resultado, não mexe no saldo
    REFUND,   // reembolso de uma BET
    ROLLBACK  // desfaz uma transação anterior (BET, WIN ou REFUND)
}
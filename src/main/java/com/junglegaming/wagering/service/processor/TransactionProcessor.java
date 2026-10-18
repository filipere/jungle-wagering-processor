package com.junglegaming.wagering.service.processor;

import com.junglegaming.wagering.domain.Money;
import com.junglegaming.wagering.domain.TransactionType;
import com.junglegaming.wagering.domain.Wallet;

// Interface que todos os processors seguem (padrão Strategy).
// Cada tipo de transação tem uma classe que sabe aplicar a regra dele na carteira.
public interface TransactionProcessor {

    // Diz qual tipo de transação esse processor trata (BET, WIN...).
    // O WageringService usa isso para montar o mapa tipo -> processor.
    TransactionType supportsType();

    // Aplica a operação na carteira. Quem salva no banco e grava o ledger é o service.
    void process(Wallet wallet, Money amount);
}
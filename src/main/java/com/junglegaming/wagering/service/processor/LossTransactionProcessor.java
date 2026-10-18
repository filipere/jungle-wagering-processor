package com.junglegaming.wagering.service.processor;

import com.junglegaming.wagering.domain.Money;
import com.junglegaming.wagering.domain.TransactionType;
import com.junglegaming.wagering.domain.Wallet;
import org.springframework.stereotype.Component;

// Processor da perda (LOSS): não mexe no saldo.
@Component
public class LossTransactionProcessor implements TransactionProcessor {

    @Override
    public TransactionType supportsType() {
        return TransactionType.LOSS;
    }

    @Override
    public void process(Wallet wallet, Money amount) {
        // LOSS só registra o resultado. O dinheiro da aposta já saiu quando a BET foi processada.
        // Como o saldo não muda, o service também não grava nada no ledger.
    }
}
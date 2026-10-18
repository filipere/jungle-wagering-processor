package com.junglegaming.wagering.service.processor;

import com.junglegaming.wagering.domain.Money;
import com.junglegaming.wagering.domain.TransactionType;
import com.junglegaming.wagering.domain.Wallet;
import org.springframework.stereotype.Component;

// Processor da aposta (BET): tira o valor da carteira.
@Component
public class BetTransactionProcessor implements TransactionProcessor {

    @Override
    public TransactionType supportsType() {
        return TransactionType.BET;
    }

    @Override
    public void process(Wallet wallet, Money amount) {
        // O debit já confere se tem saldo. Se não tiver, lança BusinessRuleException
        // (INSUFFICIENT_FUNDS) e o service rejeita a transação.
        wallet.debit(amount);
    }
}
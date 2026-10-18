package com.junglegaming.wagering.service.processor;

import com.junglegaming.wagering.domain.Money;
import com.junglegaming.wagering.domain.TransactionType;
import com.junglegaming.wagering.domain.Wallet;
import org.springframework.stereotype.Component;

// Processor do reembolso (REFUND): devolve o valor da BET para a carteira.
@Component
public class RefundTransactionProcessor implements TransactionProcessor {

    @Override
    public TransactionType supportsType() {
        return TransactionType.REFUND;
    }

    @Override
    public void process(Wallet wallet, Money amount) {
        // As conferências (se a BET existe, se já foi reembolsada...) são feitas antes,
        // no WageringService. Aqui só devolve o dinheiro.
        wallet.credit(amount);
    }
}
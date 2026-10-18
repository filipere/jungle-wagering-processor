package com.junglegaming.wagering.service.processor;

import com.junglegaming.wagering.domain.Money;
import com.junglegaming.wagering.domain.TransactionType;
import com.junglegaming.wagering.domain.Wallet;
import org.springframework.stereotype.Component;

// Processor do prêmio (WIN): coloca o valor na carteira.
@Component
public class WinTransactionProcessor implements TransactionProcessor {

    @Override
    public TransactionType supportsType() {
        return TransactionType.WIN;
    }

    @Override
    public void process(Wallet wallet, Money amount) {
        wallet.credit(amount);
    }
}
package com.junglegaming.wagering.service;

import com.junglegaming.wagering.domain.Transaction;
import com.junglegaming.wagering.domain.TransactionStatus;
import com.junglegaming.wagering.repository.TransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

// Esse worker cuida das transações que chegaram antes da referência delas.
// Exemplo: chegou um REFUND, mas a BET original ainda não chegou. O REFUND fica
// como PENDING_REFERENCE e esse worker tenta de novo de tempos em tempos.
@Slf4j
@Component
public class PendingReferenceWorker {

    private final TransactionRepository transactionRepository;
    private final WageringService wageringService;

    public PendingReferenceWorker(TransactionRepository transactionRepository, WageringService wageringService) {
        this.transactionRepository = transactionRepository;
        this.wageringService = wageringService;
    }

    // Roda sozinho a cada 10 segundos (10000 ms) depois que a execução anterior termina.
    // Só funciona porque a WageringApplication tem o @EnableScheduling.
    @Scheduled(fixedDelay = 10000)
    public void retryPendingTransactions() {
        // Busca todas as transações que estão esperando a referência
        List<Transaction> pending = transactionRepository.findByStatus(TransactionStatus.PENDING_REFERENCE);

        for (Transaction tx : pending) {
            try {
                // A regra de verdade fica no service (ele trava a carteira e tenta aplicar de novo)
                wageringService.retryPending(tx.getId(), tx.getWalletId());
            } catch (Exception e) {
                // Se uma transação der erro, loga e segue para as outras,
                // para uma só não travar o worker inteiro
                log.error("erro ao reprocessar transactionId={}", tx.getId(), e);
            }
        }
    }
}
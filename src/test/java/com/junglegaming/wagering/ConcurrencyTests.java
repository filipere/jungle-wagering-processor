package com.junglegaming.wagering;

import com.junglegaming.wagering.domain.Money;
import com.junglegaming.wagering.domain.TransactionStatus;
import com.junglegaming.wagering.domain.TransactionType;
import com.junglegaming.wagering.domain.Wallet;
import com.junglegaming.wagering.dto.TransactionRequestDto;
import com.junglegaming.wagering.dto.TransactionResponseDto;
import com.junglegaming.wagering.repository.OutboxRepository;
import com.junglegaming.wagering.repository.TransactionRepository;
import com.junglegaming.wagering.repository.WalletLedgerEntryRepository;
import com.junglegaming.wagering.repository.WalletRepository;
import com.junglegaming.wagering.service.WageringService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class ConcurrencyTests {

    private static final String WALLET_ID = "wallet-race-1";

    @Autowired
    private WageringService wageringService;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private WalletLedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
        ledgerEntryRepository.deleteAll();
        transactionRepository.deleteAll();
        walletRepository.deleteAll();

        walletRepository.save(new Wallet(WALLET_ID, "player-race", new Money(new BigDecimal("100.00"), "BRL")));
    }

    @Test
    void twoBetsOfEightyShouldProcessOnlyOne() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<TransactionResponseDto>> futures = new ArrayList<>();

        for (int i = 0; i < 2; i++) {
            String ref = "race-bet-" + i;
            futures.add(pool.submit(() -> {
                start.await();
                return wageringService.processTransaction(new TransactionRequestDto(
                        WALLET_ID, TransactionType.BET, new BigDecimal("80.00"), "BRL", ref, null));
            }));
        }
        start.countDown();

        int processed = 0;
        int rejected = 0;
        for (Future<TransactionResponseDto> future : futures) {
            TransactionStatus status = future.get(30, TimeUnit.SECONDS).status();
            if (status == TransactionStatus.PROCESSED) processed++;
            if (status == TransactionStatus.REJECTED) rejected++;
        }
        pool.shutdown();

        assertEquals(1, processed);
        assertEquals(1, rejected);
        assertEquals(0, new BigDecimal("20.00").compareTo(
                walletRepository.findById(WALLET_ID).orElseThrow().getBalance().getAmount()));
        assertEquals(1, ledgerEntryRepository.findByWalletId(WALLET_ID).size());
    }

    @Test
    void sameBetSentFiftyTimesShouldDebitOnce() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(20);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<TransactionResponseDto>> futures = new ArrayList<>();

        for (int i = 0; i < 50; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return wageringService.processTransaction(new TransactionRequestDto(
                        WALLET_ID, TransactionType.BET, new BigDecimal("10.00"), "BRL", "same-bet", null));
            }));
        }
        start.countDown();

        int firstExecutions = 0;
        for (Future<TransactionResponseDto> future : futures) {
            if (!future.get(60, TimeUnit.SECONDS).idempotentReplay()) firstExecutions++;
        }
        pool.shutdown();

        assertEquals(1, firstExecutions);
        assertEquals(0, new BigDecimal("90.00").compareTo(
                walletRepository.findById(WALLET_ID).orElseThrow().getBalance().getAmount()));
        assertEquals(1, ledgerEntryRepository.findByWalletId(WALLET_ID).size());
    }
}
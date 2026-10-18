package com.junglegaming.wagering;

import com.junglegaming.wagering.domain.*;
import com.junglegaming.wagering.dto.ReconciliationResponseDto;
import com.junglegaming.wagering.dto.TransactionRequestDto;
import com.junglegaming.wagering.dto.TransactionResponseDto;
import com.junglegaming.wagering.dto.WalletCreationRequestDto;
import com.junglegaming.wagering.exception.IdempotencyConflictException;
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

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class WageringApplicationTests {

    private static final String WALLET_ID = "wallet-test-123";

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

        walletRepository.save(new Wallet(WALLET_ID, "player-test-1", new Money(new BigDecimal("100.00"), "BRL")));
    }

    private BigDecimal balance() {
        return walletRepository.findById(WALLET_ID).orElseThrow().getBalance().getAmount();
    }

    private TransactionRequestDto request(TransactionType type, String amount, String ref, String related) {
        return new TransactionRequestDto(WALLET_ID, type, new BigDecimal(amount), "BRL", ref, related);
    }

    @Test
    void shouldDebitWalletOnBet() {
        TransactionResponseDto response = wageringService.processTransaction(
                request(TransactionType.BET, "80.00", "ref-bet-01", null));

        assertEquals(TransactionStatus.PROCESSED, response.status());
        assertEquals(0, new BigDecimal("20.00").compareTo(balance()));
        assertEquals(1, ledgerEntryRepository.findByWalletId(WALLET_ID).size());
    }

    @Test
    void shouldNotChangeBalanceOnLoss() {
        wageringService.processTransaction(request(TransactionType.LOSS, "80.00", "ref-loss-01", null));

        assertEquals(0, new BigDecimal("100.00").compareTo(balance()));
        assertEquals(0, ledgerEntryRepository.findByWalletId(WALLET_ID).size());
    }

    @Test
    void shouldCreditWalletOnWin() {
        wageringService.processTransaction(request(TransactionType.WIN, "50.00", "ref-win-01", null));

        assertEquals(0, new BigDecimal("150.00").compareTo(balance()));
    }

    @Test
    void shouldRejectBetWithInsufficientFunds() {
        TransactionResponseDto response = wageringService.processTransaction(
                request(TransactionType.BET, "150.00", "ref-bet-big", null));

        assertEquals(TransactionStatus.REJECTED, response.status());
        assertEquals(FailureCode.INSUFFICIENT_FUNDS, response.failureCode());
        assertEquals(0, new BigDecimal("100.00").compareTo(balance()));
        assertEquals(0, ledgerEntryRepository.findByWalletId(WALLET_ID).size());
    }

    @Test
    void shouldReturnSameResultWhenReferenceIdIsRepeated() {
        TransactionRequestDto req = request(TransactionType.BET, "30.00", "ref-repeat-01", null);

        TransactionResponseDto first = wageringService.processTransaction(req);
        TransactionResponseDto second = wageringService.processTransaction(req);

        assertFalse(first.idempotentReplay());
        assertTrue(second.idempotentReplay());
        assertEquals(first.transactionId(), second.transactionId());
        assertEquals(0, new BigDecimal("70.00").compareTo(balance()));
    }

    @Test
    void shouldFailWhenSameKeyHasDifferentPayload() {
        wageringService.processTransaction(request(TransactionType.BET, "30.00", "ref-conflict-01", null));

        assertThrows(IdempotencyConflictException.class, () ->
                wageringService.processTransaction(request(TransactionType.BET, "40.00", "ref-conflict-01", null)));
    }

    @Test
    void shouldRefundBetOnlyOnce() {
        wageringService.processTransaction(request(TransactionType.BET, "40.00", "bet-1", null));

        TransactionResponseDto refund = wageringService.processTransaction(
                request(TransactionType.REFUND, "40.00", "refund-1", "bet-1"));
        assertEquals(TransactionStatus.PROCESSED, refund.status());
        assertEquals(0, new BigDecimal("100.00").compareTo(balance()));

        TransactionResponseDto refundAgain = wageringService.processTransaction(
                request(TransactionType.REFUND, "40.00", "refund-2", "bet-1"));
        assertEquals(TransactionStatus.REJECTED, refundAgain.status());
        assertEquals(FailureCode.ALREADY_REVERSED, refundAgain.failureCode());
        assertEquals(0, new BigDecimal("100.00").compareTo(balance()));
    }

    @Test
    void shouldRollbackWin() {
        wageringService.processTransaction(request(TransactionType.WIN, "50.00", "win-1", null));

        TransactionResponseDto rollback = wageringService.processTransaction(
                request(TransactionType.ROLLBACK, "50.00", "rollback-1", "win-1"));

        assertEquals(TransactionStatus.PROCESSED, rollback.status());
        assertEquals(0, new BigDecimal("100.00").compareTo(balance()));
    }

    @Test
    void shouldKeepRefundPendingWhenReferenceDoesNotExist() {
        TransactionResponseDto response = wageringService.processTransaction(
                request(TransactionType.REFUND, "40.00", "refund-early", "bet-that-will-come"));

        assertEquals(TransactionStatus.PENDING_REFERENCE, response.status());
        assertEquals(0, new BigDecimal("100.00").compareTo(balance()));
    }

    @Test
    void ledgerShouldMatchWalletBalance() {
        Wallet wallet = wageringService.createWallet(
                new WalletCreationRequestDto("player-recon", new BigDecimal("200.00"), "BRL"));

        wageringService.processTransaction(new TransactionRequestDto(
                wallet.getId(), TransactionType.BET, new BigDecimal("50.00"), "BRL", "recon-bet", null));
        wageringService.processTransaction(new TransactionRequestDto(
                wallet.getId(), TransactionType.WIN, new BigDecimal("20.00"), "BRL", "recon-win", null));

        ReconciliationResponseDto result = wageringService.reconcile(wallet.getId());

        assertTrue(result.consistent());
        assertEquals("170.00", result.storedBalance());
        assertEquals("170.00", result.calculatedBalance());
    }
}
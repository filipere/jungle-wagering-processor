package com.junglegaming.wagering.service;

import com.junglegaming.wagering.domain.*;
import com.junglegaming.wagering.dto.ReconciliationResponseDto;
import com.junglegaming.wagering.dto.TransactionRequestDto;
import com.junglegaming.wagering.dto.TransactionResponseDto;
import com.junglegaming.wagering.dto.WalletCreationRequestDto;
import com.junglegaming.wagering.exception.BusinessRuleException;
import com.junglegaming.wagering.exception.IdempotencyConflictException;
import com.junglegaming.wagering.exception.NotFoundException;
import com.junglegaming.wagering.repository.OutboxRepository;
import com.junglegaming.wagering.repository.TransactionRepository;
import com.junglegaming.wagering.repository.WalletLedgerEntryRepository;
import com.junglegaming.wagering.repository.WalletRepository;
import com.junglegaming.wagering.service.processor.TransactionProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class WageringService {

    // depois de 5 tentativas sem achar a referência, a transação é rejeitada
    private static final int MAX_ATTEMPTS = 5;

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final WalletLedgerEntryRepository ledgerEntryRepository;
    private final OutboxRepository outboxRepository;
    private final Map<TransactionType, TransactionProcessor> processorsMap;

    public WageringService(
            WalletRepository walletRepository,
            TransactionRepository transactionRepository,
            WalletLedgerEntryRepository ledgerEntryRepository,
            OutboxRepository outboxRepository,
            List<TransactionProcessor> processors
    ) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.outboxRepository = outboxRepository;
        this.processorsMap = processors.stream()
                .collect(Collectors.toMap(TransactionProcessor::supportsType, Function.identity()));
    }

    @Transactional
    public Wallet createWallet(WalletCreationRequestDto request) {
        if (request.playerId() == null || request.playerId().isBlank()) {
            throw new IllegalArgumentException("O playerId é obrigatório.");
        }
        Money initialMoney = new Money(request.initialBalance(), request.currency());

        String walletId = UUID.randomUUID().toString();
        Wallet wallet = new Wallet(walletId, request.playerId(), initialMoney);
        // saveAndFlush para o erro de carteira duplicada acontecer aqui
        walletRepository.saveAndFlush(wallet);

        if (initialMoney.getAmount().compareTo(BigDecimal.ZERO) > 0) {
            Transaction opening = new Transaction(
                    UUID.randomUUID().toString(),
                    walletId,
                    TransactionType.OPENING,
                    initialMoney,
                    "opening-" + walletId,
                    null
            );
            opening.markProcessed(initialMoney.getAmount());
            transactionRepository.saveAndFlush(opening);

            ledgerEntryRepository.save(new WalletLedgerEntry(
                    UUID.randomUUID().toString(),
                    walletId,
                    opening.getId(),
                    LedgerDirection.CREDIT,
                    initialMoney,
                    Money.zero(request.currency()),
                    initialMoney
            ));
        }

        return wallet;
    }

    @Transactional
    public TransactionResponseDto processTransaction(TransactionRequestDto request) {
        if (request.type() == TransactionType.OPENING) {
            throw new IllegalArgumentException("OPENING é interna e não pode ser enviada.");
        }
        boolean needsReference = request.type() == TransactionType.REFUND
                || request.type() == TransactionType.ROLLBACK;
        if (needsReference && (request.relatedReferenceId() == null || request.relatedReferenceId().isBlank())) {
            throw new IllegalArgumentException("REFUND e ROLLBACK precisam do relatedReferenceId.");
        }

        Money amount = new Money(request.amount(), request.currency());

        // Trava a carteira antes de checar a idempotência. Assim, quem chega ao mesmo tempo
        // espera na fila e já enxerga a transação que a primeira commitou.
        Wallet wallet = findWalletWithLock(request.walletId());

        Transaction existing = transactionRepository.findByReferenceId(request.referenceId()).orElse(null);
        if (existing != null) {
            if (!isSamePayload(existing, request)) {
                throw new IdempotencyConflictException(
                        "O referenceId " + request.referenceId() + " já foi usado com outros dados.");
            }
            log.info("transação repetida transactionId={} walletId={}", existing.getId(), existing.getWalletId());
            return TransactionResponseDto.from(existing, true);
        }

        Transaction tx = transactionRepository.save(new Transaction(
                UUID.randomUUID().toString(),
                wallet.getId(),
                request.type(),
                amount,
                request.referenceId(),
                request.relatedReferenceId()
        ));

        apply(tx, wallet);

        log.info("transação transactionId={} walletId={} status={}", tx.getId(), wallet.getId(), tx.getStatus());
        return TransactionResponseDto.from(tx, false);
    }

    // Chamado pelo worker para tentar de novo as transações que esperam a referência
    @Transactional
    public void retryPending(String transactionId, String walletId) {
        Wallet wallet = findWalletWithLock(walletId);
        Transaction tx = transactionRepository.findById(transactionId).orElse(null);

        // outra instância pode já ter tratado
        if (tx == null || tx.getStatus() != TransactionStatus.PENDING_REFERENCE) {
            return;
        }
        apply(tx, wallet);
        log.info("retry transactionId={} status={} attempts={}", tx.getId(), tx.getStatus(), tx.getAttempts());
    }

    private void apply(Transaction tx, Wallet wallet) {
        Money balanceBefore = wallet.getBalance();
        try {
            if (!wallet.getBalance().getCurrency().equals(tx.getMoney().getCurrency())) {
                throw new BusinessRuleException(FailureCode.CURRENCY_MISMATCH,
                        "A moeda é diferente da moeda da carteira.");
            }

            Transaction reference = null;
            if (tx.requiresReference()) {
                reference = transactionRepository.findByReferenceId(tx.getRelatedReferenceId()).orElse(null);
                if (reference == null || reference.getStatus() == TransactionStatus.PENDING_REFERENCE) {
                    handleMissingReference(tx, wallet);
                    return;
                }
                validateReference(tx, reference);
            }

            if (tx.getType() == TransactionType.ROLLBACK) {
                rollback(wallet, tx.getMoney(), reference);
            } else {
                processorsMap.get(tx.getType()).process(wallet, tx.getMoney());
            }

            Money balanceAfter = wallet.getBalance();
            tx.markProcessed(balanceAfter.getAmount());

            // se o saldo mudou, grava no ledger e na outbox
            int compare = balanceAfter.getAmount().compareTo(balanceBefore.getAmount());
            if (compare != 0) {
                LedgerDirection direction = compare > 0 ? LedgerDirection.CREDIT : LedgerDirection.DEBIT;
                ledgerEntryRepository.save(new WalletLedgerEntry(
                        UUID.randomUUID().toString(),
                        wallet.getId(),
                        tx.getId(),
                        direction,
                        tx.getMoney(),
                        balanceBefore,
                        balanceAfter
                ));
                saveEvent(wallet.getId(), "WalletBalanceChanged", String.format(
                        "{\"walletId\":\"%s\",\"transactionId\":\"%s\",\"balanceBefore\":\"%s\",\"balanceAfter\":\"%s\"}",
                        wallet.getId(), tx.getId(), balanceBefore.getAmount(), balanceAfter.getAmount()));
            }

            saveEvent(wallet.getId(), "WagerTransactionProcessed", String.format(
                    "{\"walletId\":\"%s\",\"transactionId\":\"%s\",\"type\":\"%s\"}",
                    wallet.getId(), tx.getId(), tx.getType()));

        } catch (BusinessRuleException e) {
            reject(tx, wallet, e.getFailureCode());
        }
    }

    private void rollback(Wallet wallet, Money amount, Transaction reference) {
        // reverter uma BET devolve o dinheiro, reverter WIN ou REFUND tira o dinheiro de volta
        if (reference.getType() == TransactionType.BET) {
            wallet.credit(amount);
            return;
        }
        try {
            wallet.debit(amount);
        } catch (BusinessRuleException e) {
            throw new BusinessRuleException(FailureCode.ROLLBACK_INSUFFICIENT_FUNDS,
                    "A reversão deixaria o saldo negativo.");
        }
    }

    private void validateReference(Transaction tx, Transaction reference) {
        if (reference.getStatus() != TransactionStatus.PROCESSED
                || !reference.getWalletId().equals(tx.getWalletId())) {
            throw new BusinessRuleException(FailureCode.INVALID_REFERENCE, "Referência inválida.");
        }
        if (reference.getMoney().getAmount().compareTo(tx.getMoney().getAmount()) != 0) {
            throw new BusinessRuleException(FailureCode.AMOUNT_MISMATCH,
                    "O valor deve ser igual ao da referência.");
        }

        TransactionType refType = reference.getType();
        boolean validType;
        if (tx.getType() == TransactionType.REFUND) {
            validType = refType == TransactionType.BET;
        } else {
            validType = refType == TransactionType.BET || refType == TransactionType.WIN
                    || refType == TransactionType.REFUND;
        }
        if (!validType) {
            throw new BusinessRuleException(FailureCode.INVALID_REFERENCE,
                    tx.getType() + " não pode referenciar " + refType);
        }

        if (transactionRepository.existsByRelatedReferenceIdAndTypeAndStatus(
                reference.getReferenceId(), tx.getType(), TransactionStatus.PROCESSED)) {
            throw new BusinessRuleException(FailureCode.ALREADY_REVERSED, "A referência já foi revertida.");
        }
    }

    private void handleMissingReference(Transaction tx, Wallet wallet) {
        if (tx.getAttempts() >= MAX_ATTEMPTS) {
            reject(tx, wallet, FailureCode.REFERENCE_NOT_FOUND);
            return;
        }
        boolean firstTime = tx.getAttempts() == 0;
        tx.markPendingReference();

        if (firstTime) {
            saveEvent(wallet.getId(), "WagerTransactionPendingReference", String.format(
                    "{\"walletId\":\"%s\",\"transactionId\":\"%s\"}", wallet.getId(), tx.getId()));
        }
    }

    private void reject(Transaction tx, Wallet wallet, FailureCode code) {
        tx.reject(code, wallet.getBalance().getAmount());
        saveEvent(wallet.getId(), "WagerTransactionRejected", String.format(
                "{\"walletId\":\"%s\",\"transactionId\":\"%s\",\"failureCode\":\"%s\"}",
                wallet.getId(), tx.getId(), code));
    }

    private void saveEvent(String walletId, String eventType, String payload) {
        outboxRepository.save(new OutboxMessage(
                UUID.randomUUID().toString(), walletId, eventType, payload, Instant.now()));
    }

    private boolean isSamePayload(Transaction existing, TransactionRequestDto request) {
        return existing.getType() == request.type()
                && existing.getWalletId().equals(request.walletId())
                && existing.getMoney().getAmount().compareTo(request.amount()) == 0
                && existing.getMoney().getCurrency().equals(request.currency())
                && Objects.equals(existing.getRelatedReferenceId(), request.relatedReferenceId());
    }

    private Wallet findWalletWithLock(String walletId) {
        return walletRepository.findByIdWithLock(walletId)
                .orElseThrow(() -> new NotFoundException("Carteira não encontrada: " + walletId));
    }

    // ---------- consultas ----------

    @Transactional(readOnly = true)
    public Wallet getWallet(String walletId) {
        return walletRepository.findById(walletId)
                .orElseThrow(() -> new NotFoundException("Carteira não encontrada: " + walletId));
    }

    @Transactional(readOnly = true)
    public TransactionResponseDto getTransaction(String transactionId) {
        Transaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new NotFoundException("Transação não encontrada: " + transactionId));
        return TransactionResponseDto.from(tx, false);
    }

    @Transactional(readOnly = true)
    public List<WalletLedgerEntry> getLedger(String walletId, int page, int size) {
        getWallet(walletId);
        return ledgerEntryRepository.findByWalletIdOrderByCreatedAtAscIdAsc(walletId, PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public ReconciliationResponseDto reconcile(String walletId) {
        Wallet wallet = getWallet(walletId);
        List<WalletLedgerEntry> entries = ledgerEntryRepository.findByWalletId(walletId);

        BigDecimal calculated = BigDecimal.ZERO;
        for (WalletLedgerEntry entry : entries) {
            if (entry.getDirection() == LedgerDirection.CREDIT) {
                calculated = calculated.add(entry.getMoney().getAmount());
            } else {
                calculated = calculated.subtract(entry.getMoney().getAmount());
            }
        }

        BigDecimal stored = wallet.getBalance().getAmount();
        BigDecimal difference = stored.subtract(calculated);
        boolean consistent = difference.compareTo(BigDecimal.ZERO) == 0;

        if (!consistent) {
            log.error("divergência na reconciliação walletId={} stored={} calculated={}",
                    walletId, stored, calculated);
        }

        return new ReconciliationResponseDto(walletId, stored.toPlainString(), calculated.toPlainString(),
                difference.toPlainString(), consistent, entries.size());
    }
}
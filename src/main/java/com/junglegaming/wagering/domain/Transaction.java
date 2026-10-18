package com.junglegaming.wagering.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;

// Guarda cada operação que o provedor enviou (BET, WIN...). É a tabela "transactions".
@Entity
@Table(name = "transactions")
@Getter
@NoArgsConstructor
public class Transaction {

    @Id
    private String id;

    @Column(name = "wallet_id", nullable = false)
    private String walletId;

    @Enumerated(EnumType.STRING) // guarda o nome do enum como texto
    @Column(nullable = false)
    private TransactionType type;

    // Valor e moeda da operação (colunas amount e currency)
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "amount", nullable = false)),
            @AttributeOverride(name = "currency", column = @Column(name = "currency", nullable = false))
    })
    private Money money;

    // Chave de idempotência. É UNIQUE no banco: não existem duas transações com o mesmo referenceId.
    @Column(name = "reference_id", nullable = false)
    private String referenceId;

    // Só para REFUND e ROLLBACK: o referenceId da transação original
    @Column(name = "related_reference_id")
    private String relatedReferenceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus status;

    // Só tem valor quando a transação foi rejeitada
    @Enumerated(EnumType.STRING)
    @Column(name = "failure_code")
    private FailureCode failureCode;

    // Saldo da carteira depois da operação. Serve para devolver o mesmo saldo se a requisição for repetida.
    @Column(name = "balance_after")
    private BigDecimal balanceAfter;

    // Quantas vezes já tentei achar a referência
    @Column(nullable = false)
    private int attempts;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Transaction(String id, String walletId, TransactionType type, Money money,
                       String referenceId, String relatedReferenceId) {
        this.id = id;
        this.walletId = walletId;
        this.type = type;
        this.money = money;
        this.referenceId = referenceId;
        this.relatedReferenceId = relatedReferenceId;
        this.status = TransactionStatus.PENDING; // toda transação nasce PENDING
        this.attempts = 0;
        this.createdAt = Instant.now();
    }

    // REFUND e ROLLBACK dependem de outra transação
    public boolean requiresReference() {
        return type == TransactionType.REFUND || type == TransactionType.ROLLBACK;
    }

    // PROCESSED e REJECTED são finais, não mudam mais.
    // Por isso cada método abaixo confere antes de mudar o status.
    public void markProcessed(BigDecimal balanceAfter) {
        checkNotFinished();
        this.status = TransactionStatus.PROCESSED;
        this.balanceAfter = balanceAfter;
    }

    public void reject(FailureCode code, BigDecimal balanceAfter) {
        checkNotFinished();
        this.status = TransactionStatus.REJECTED;
        this.failureCode = code;
        this.balanceAfter = balanceAfter;
    }

    // Referência não encontrada: fica esperando e conta mais uma tentativa
    public void markPendingReference() {
        checkNotFinished();
        this.status = TransactionStatus.PENDING_REFERENCE;
        this.attempts++;
    }

    // Se tentarem mudar uma transação finalizada, é erro de programação (não é regra de negócio)
    private void checkNotFinished() {
        if (status == TransactionStatus.PROCESSED || status == TransactionStatus.REJECTED) {
            throw new IllegalStateException("A transação " + id + " já foi finalizada: " + status);
        }
    }
}
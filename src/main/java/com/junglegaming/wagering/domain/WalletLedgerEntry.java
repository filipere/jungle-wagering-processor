package com.junglegaming.wagering.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

// Uma linha do ledger (livro de lançamentos). É a tabela "wallet_ledger_entries".
// Só tem getters e nenhum método que altere os dados: depois de criada, a linha não muda.
@Entity
@Table(name = "wallet_ledger_entries")
@Getter
@NoArgsConstructor(force = true)
public class WalletLedgerEntry {

    @Id
    private String id;

    @Column(name = "wallet_id", nullable = false)
    private String walletId;

    // Qual transação gerou esse lançamento
    @Column(name = "transaction_id", nullable = false)
    private String transactionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LedgerDirection direction;

    // Valor movimentado
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "amount", nullable = false)),
            @AttributeOverride(name = "currency", column = @Column(name = "currency", nullable = false))
    })
    private Money money;

    // Saldo antes do lançamento
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "balance_before", nullable = false)),
            @AttributeOverride(name = "currency", column = @Column(name = "balance_currency_before", nullable = false))
    })
    private Money balanceBefore;

    // Saldo depois do lançamento
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "balance_after", nullable = false)),
            @AttributeOverride(name = "currency", column = @Column(name = "balance_currency_after", nullable = false))
    })
    private Money balanceAfter;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public WalletLedgerEntry(
            String id,
            String walletId,
            String transactionId,
            LedgerDirection direction,
            Money money,
            Money balanceBefore,
            Money balanceAfter
    ) {
        this.id = id;
        this.walletId = walletId;
        this.transactionId = transactionId;
        this.direction = direction;
        this.money = money;
        this.balanceBefore = balanceBefore;
        this.balanceAfter = balanceAfter;
        this.createdAt = LocalDateTime.now();
    }
}
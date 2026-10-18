package com.junglegaming.wagering.domain;

import com.junglegaming.wagering.exception.BusinessRuleException;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

// A carteira do jogador. É a tabela "wallets".
@Entity
@Table(name = "wallets")
@Getter
@NoArgsConstructor // o JPA precisa de um construtor vazio
public class Wallet {

    @Id
    private String id;

    @Column(name = "player_id", nullable = false)
    private String playerId;

    // O saldo é um Money. O @AttributeOverride define o nome das colunas no banco
    // (balance_amount e balance_currency).
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "balance_amount", nullable = false)),
            @AttributeOverride(name = "currency", column = @Column(name = "balance_currency", nullable = false))
    })
    private Money balance;

    // Começa em 1 e só aumenta quando o saldo muda (débito ou crédito)
    @Column(nullable = false)
    private int version;

    public Wallet(String id, String playerId, Money balance) {
        this.id = id;
        this.playerId = playerId;
        this.balance = balance;
        this.version = 1;
    }

    // Tira dinheiro. Antes confere se tem saldo, para nunca ficar negativo.
    public void debit(Money amount) {
        if (this.balance.getAmount().compareTo(amount.getAmount()) < 0) {
            throw new BusinessRuleException(FailureCode.INSUFFICIENT_FUNDS, "Saldo insuficiente.");
        }
        this.balance = this.balance.subtract(amount);
        this.version++;
    }

    // Coloca dinheiro
    public void credit(Money amount) {
        this.balance = this.balance.add(amount);
        this.version++;
    }
}
package com.junglegaming.wagering.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

// Representa dinheiro: um valor e uma moeda.
// Uso BigDecimal em vez de double porque double perde precisão em decimais.
// @Embeddable faz o Money virar duas colunas (valor e moeda) dentro da tabela que usar ele.
@Embeddable
@Getter
@NoArgsConstructor(force = true) // o JPA precisa de um construtor vazio
public class Money {

    // Na resposta da API o valor sai como texto ("25.00")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal amount;
    private String currency;

    public Money(BigDecimal amount, String currency) {
        // Não aceita valor nulo nem negativo
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("O valor monetário não pode ser nulo ou negativo.");
        }
        // Não aceita mais de 2 casas decimais (ex.: 10.005).
        // O stripTrailingZeros evita reclamar de 10.500, que tem zero sobrando.
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("O valor deve ter no máximo 2 casas decimais.");
        }
        // Deixa sempre com 2 casas (10 vira 10.00)
        this.amount = amount.setScale(2, RoundingMode.HALF_EVEN);
        this.currency = Objects.requireNonNull(currency, "A moeda não pode ser nula.");
    }

    // Atalho para criar um Money zerado (usado no saldo anterior da abertura da carteira)
    public static Money zero(String currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    // Soma. Devolve um Money novo, o original não muda (Money é imutável).
    public Money add(Money other) {
        validateCurrency(other);
        return new Money(this.amount.add(other.amount), this.currency);
    }

    // Subtrai. Se o resultado ficar negativo, lança erro.
    public Money subtract(Money other) {
        validateCurrency(other);
        BigDecimal newAmount = this.amount.subtract(other.amount);
        if (newAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Fundos insuficientes.");
        }
        return new Money(newAmount, this.currency);
    }

    // Não dá para somar BRL com USD
    private void validateCurrency(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException("Moedas incompatíveis: " + this.currency + " vs " + other.currency);
        }
    }
}
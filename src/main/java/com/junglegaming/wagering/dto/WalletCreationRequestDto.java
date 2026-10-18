package com.junglegaming.wagering.dto;

import java.math.BigDecimal;

// O que o cliente envia para criar uma carteira.
// As validações (valor negativo, casas decimais) são feitas pelo Money no service.
public record WalletCreationRequestDto(
        String playerId,
        BigDecimal initialBalance,
        String currency
) {}
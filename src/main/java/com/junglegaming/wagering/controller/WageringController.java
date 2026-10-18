package com.junglegaming.wagering.controller;

import com.junglegaming.wagering.dto.TransactionRequestDto;
import com.junglegaming.wagering.dto.TransactionResponseDto;
import com.junglegaming.wagering.service.WageringService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// Controller das transações de aposta. Só recebe a requisição e chama o service,
// a regra de negócio fica toda no WageringService.
@RestController
@RequestMapping("/api/wagering")
@RequiredArgsConstructor // o Lombok cria o construtor com o service
public class WageringController {

    private final WageringService wageringService;

    // POST /api/wagering/transactions
    // @Valid ativa as validações do DTO (campos obrigatórios, valor mínimo...)
    @PostMapping("/transactions")
    public ResponseEntity<TransactionResponseDto> processTransaction(@RequestBody @Valid TransactionRequestDto request) {
        TransactionResponseDto response = wageringService.processTransaction(request);

        // Escolhe o status HTTP de acordo com o que aconteceu com a transação,
        // para o provedor saber o resultado sem ler a mensagem
        HttpStatus status = switch (response.status()) {
            // processada: 201 na primeira vez, 200 se foi só uma repetição
            case PROCESSED -> response.idempotentReplay() ? HttpStatus.OK : HttpStatus.CREATED;
            // rejeitada por regra de negócio (ex.: sem saldo)
            case REJECTED -> HttpStatus.UNPROCESSABLE_ENTITY;
            // aceita, mas ainda esperando a referência
            case PENDING, PENDING_REFERENCE -> HttpStatus.ACCEPTED;
        };
        return ResponseEntity.status(status).body(response);
    }

    // GET /api/wagering/transactions/{transactionId}
    // Consulta uma transação pelo id
    @GetMapping("/transactions/{transactionId}")
    public TransactionResponseDto getTransaction(@PathVariable String transactionId) {
        return wageringService.getTransaction(transactionId);
    }
}
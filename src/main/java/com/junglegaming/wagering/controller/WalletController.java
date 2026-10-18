package com.junglegaming.wagering.controller;

import com.junglegaming.wagering.domain.Wallet;
import com.junglegaming.wagering.domain.WalletLedgerEntry;
import com.junglegaming.wagering.dto.ReconciliationResponseDto;
import com.junglegaming.wagering.dto.WalletCreationRequestDto;
import com.junglegaming.wagering.service.WageringService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Controller das carteiras: criar, consultar, ver o ledger e reconciliar.
@RestController
@RequestMapping("/api/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WageringService wageringService;

    // POST /api/wallets
    // Cria a carteira. Se já existir uma do mesmo jogador na mesma moeda, dá 409.
    @PostMapping
    public ResponseEntity<Wallet> createWallet(@RequestBody WalletCreationRequestDto request) {
        Wallet wallet = wageringService.createWallet(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(wallet);
    }

    // GET /api/wallets/{walletId}
    @GetMapping("/{walletId}")
    public Wallet getWallet(@PathVariable String walletId) {
        return wageringService.getWallet(walletId);
    }

    // GET /api/wallets/{walletId}/ledger?page=0&size=50
    // Lista os lançamentos com paginação. Limitei o size em 100 para ninguém pedir tudo de uma vez.
    @GetMapping("/{walletId}/ledger")
    public List<WalletLedgerEntry> getLedger(@PathVariable String walletId,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "50") int size) {
        return wageringService.getLedger(walletId, page, Math.min(size, 100));
    }

    // POST /api/wallets/{walletId}/reconciliation
    // Soma o ledger e compara com o saldo da carteira
    @PostMapping("/{walletId}/reconciliation")
    public ReconciliationResponseDto reconcile(@PathVariable String walletId) {
        return wageringService.reconcile(walletId);
    }
}
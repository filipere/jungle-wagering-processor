package com.junglegaming.wagering.repository;

import com.junglegaming.wagering.domain.WalletLedgerEntry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

// Acesso à tabela wallet_ledger_entries
@Repository
public interface WalletLedgerEntryRepository extends JpaRepository<WalletLedgerEntry, String> {

    // Todos os lançamentos da carteira (usado na reconciliação)
    List<WalletLedgerEntry> findByWalletId(String walletId);

    // Lançamentos paginados, do mais antigo para o mais novo.
    // O id entra na ordenação para desempatar lançamentos com a mesma data.
    List<WalletLedgerEntry> findByWalletIdOrderByCreatedAtAscIdAsc(String walletId, Pageable pageable);
}
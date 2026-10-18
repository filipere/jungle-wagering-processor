package com.junglegaming.wagering.repository;

import com.junglegaming.wagering.domain.Transaction;
import com.junglegaming.wagering.domain.TransactionStatus;
import com.junglegaming.wagering.domain.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

// Acesso à tabela transactions.
// Os métodos são criados pelo Spring só pelo nome (findBy..., existsBy...).
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, String> {

    // Usado na idempotência e para achar a transação original de um REFUND/ROLLBACK
    Optional<Transaction> findByReferenceId(String referenceId);

    // Confere se uma transação já foi revertida por esse tipo (evita reverter duas vezes)
    boolean existsByRelatedReferenceIdAndTypeAndStatus(String relatedReferenceId,
                                                       TransactionType type,
                                                       TransactionStatus status);

    // Usado pelo worker para listar as que estão esperando a referência
    List<Transaction> findByStatus(TransactionStatus status);
}
package com.junglegaming.wagering.repository;

import com.junglegaming.wagering.domain.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

// Acesso à tabela wallets. O Spring cria a implementação sozinho.
@Repository
public interface WalletRepository extends JpaRepository<Wallet, String> {

    // Busca a carteira e trava a linha no banco (SELECT ... FOR UPDATE).
    // Quem tentar mexer na mesma carteira ao mesmo tempo espera a transação terminar.
    // Precisa ser JPQL: o Hibernate 7 não aceita @Lock com query nativa.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.id = :id")
    Optional<Wallet> findByIdWithLock(@Param("id") String id);
}
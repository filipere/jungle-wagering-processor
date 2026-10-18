package com.junglegaming.wagering.repository;

import com.junglegaming.wagering.domain.OutboxMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

// Acesso à tabela outbox_messages
@Repository
public interface OutboxRepository extends JpaRepository<OutboxMessage, String> {

    // Eventos que ainda não foram publicados (o publisher usaria isso, mas ainda não foi feito)
    List<OutboxMessage> findByPublishedAtIsNull();
}
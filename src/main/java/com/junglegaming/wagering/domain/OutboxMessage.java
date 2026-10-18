package com.junglegaming.wagering.domain;

import jakarta.persistence.*;
import java.time.Instant;

// Evento guardado para ser publicado depois (padrão Outbox). É a tabela "outbox_messages".
// O evento é salvo na mesma transação do saldo, então nunca existe evento de algo que não foi confirmado.
@Entity
@Table(name = "outbox_messages")
public class OutboxMessage {

    @Id
    private String id;

    // Id da carteira que gerou o evento
    @Column(nullable = false)
    private String aggregateId;

    // Nome do evento (ex.: WalletBalanceChanged)
    @Column(nullable = false)
    private String eventType;

    // Conteúdo do evento em JSON
    @Column(columnDefinition = "TEXT", nullable = false)
    private String payload;

    @Column(nullable = false)
    private Instant occurredAt;

    // Fica nulo até o evento ser publicado (ainda não existe o publisher)
    private Instant publishedAt;

    protected OutboxMessage() {}

    public OutboxMessage(String id, String aggregateId, String eventType, String payload, Instant occurredAt) {
        this.id = id;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.occurredAt = occurredAt;
    }

    public boolean isPublished() {
        return publishedAt != null;
    }

    public void markPublished(Instant at) {
        this.publishedAt = at;
    }

    // Getters
    public String getId() { return id; }
    public String getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getPublishedAt() { return publishedAt; }
}
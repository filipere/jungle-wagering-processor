# Arquitetura do Processador de Apostas

Serviço que processa transações de apostas (BET, WIN, LOSS, REFUND, ROLLBACK) mantendo o saldo
da carteira consistente com o ledger, mesmo com requisições duplicadas ou concorrentes.

## Stack

- Java 21, Spring Boot 4, Spring Data JPA (Hibernate 7)
- PostgreSQL 15 (Docker)
- Flyway (migrations)
- Maven e Lombok

> O enunciado sugeria Bun/NestJS/TypeScript. Esta solução foi feita em Java/Spring Boot.

### Por que JPA/Hibernate
Como a solução é em Java, usei o JPA com Hibernate (que já vem no Spring Data). O `Money` é um
`@Embeddable`, então o valor e a moeda ficam em colunas separadas (`NUMERIC(19,2)` e `VARCHAR`),
sem perder precisão. O controle de transação é feito com `@Transactional` nos services.

## Decisões

### 1. Concorrência: lock pessimista
A carteira é lida com `PESSIMISTIC_WRITE` (`SELECT ... FOR UPDATE`) em `findByIdWithLock`.
Requisições da mesma carteira entram em fila no banco e carteiras diferentes rodam em paralelo.
Escolhi o lock pessimista porque é mais simples que o otimista com retry, e o lock fica no
banco, então funciona com várias instâncias da aplicação.
Obs.: o Hibernate 7 não aceita `@Lock` com query nativa, por isso a query é JPQL.

### 2. Dinheiro
Uso `BigDecimal` com 2 casas, nunca `double` ou `float`. A classe `Money` rejeita valor negativo,
nulo ou com mais de 2 casas. No banco: `NUMERIC(19,2)` e a moeda em coluna separada.
O modelo aceita várias moedas; moedas diferentes na mesma operação são rejeitadas
(`CURRENCY_MISMATCH`).

### 3. Ledger
Cada operação que muda o saldo grava uma linha em `wallet_ledger_entries` com direção, valor,
saldo antes e saldo depois. O código nunca atualiza nem apaga essas linhas.
LOSS e transações rejeitadas não geram lançamento.
A reconciliação (`POST /api/wallets/{id}/reconciliation`) soma o ledger e compara com o saldo.
Se divergir, loga o erro e retorna `consistent: false`.

### 4. Idempotência
A chave é o `referenceId` do body, com `UNIQUE` no banco. O lock da carteira é pego antes de
checar a chave, então duas requisições iguais ao mesmo tempo não debitam duas vezes.
- Mesma chave e mesmos dados: devolve o resultado original com `idempotentReplay: true`.
- Mesma chave e dados diferentes: 409.

### 5. Outbox
Os eventos são gravados em `outbox_messages` na mesma transação do saldo e do ledger:
`WalletBalanceChanged` (só quando o saldo muda), `WagerTransactionProcessed`,
`WagerTransactionRejected` e `WagerTransactionPendingReference`.
Nada é publicado antes do commit, porque o evento só existe se a transação inteira der certo.

### 6. Estados da transação
```
PENDING           -> PROCESSED | REJECTED | PENDING_REFERENCE
PENDING_REFERENCE -> PROCESSED | REJECTED | PENDING_REFERENCE
```
PROCESSED e REJECTED são finais. Tentar mudar uma transação final lança `IllegalStateException`.

### 7. REFUND e ROLLBACK
- Precisam do `relatedReferenceId` (o `referenceId` da transação original).
- A original precisa estar PROCESSED, ser da mesma carteira e ter o mesmo valor.
- REFUND só referencia BET. ROLLBACK referencia BET, WIN ou REFUND.
- A mesma referência não pode ser revertida duas vezes pelo mesmo tipo.
- ROLLBACK de BET devolve o dinheiro; de WIN ou REFUND tira o dinheiro de volta.
- Se a reversão deixaria o saldo negativo: `ROLLBACK_INSUFFICIENT_FUNDS`
  (diferente de `INSUFFICIENT_FUNDS`, que é aposta sem saldo).

### 8. Referência fora de ordem
Se a original ainda não existe, a transação fica `PENDING_REFERENCE` (HTTP 202).
O `PendingReferenceWorker` tenta de novo a cada 10 segundos. Depois de 5 tentativas
vira REJECTED com `REFERENCE_NOT_FOUND` (cerca de 50 segundos de espera no total, suficiente
para atrasos curtos de entrega).

### 9. Constraints no banco
- `UNIQUE (reference_id)` em transactions
- `UNIQUE (player_id, balance_currency)` em wallets
- `CHECK (balance_amount >= 0)` em wallets
- `CHECK (amount > 0)` em transactions e no ledger
- `UNIQUE (wallet_id, transaction_id)` no ledger

## Códigos de falha

| Código | Significado | O provedor deve |
|---|---|---|
| INSUFFICIENT_FUNDS | BET sem saldo | desistir ou reenviar com nova chave |
| ROLLBACK_INSUFFICIENT_FUNDS | reversão deixaria saldo negativo | tratar manualmente |
| CURRENCY_MISMATCH | moeda diferente da carteira | corrigir o payload |
| INVALID_REFERENCE | referência inválida (tipo, carteira ou não processada) | corrigir o payload |
| AMOUNT_MISMATCH | valor diferente da referência | corrigir o payload |
| ALREADY_REVERSED | referência já revertida | desistir |
| REFERENCE_NOT_FOUND | referência não chegou a tempo | reenviar a referência e depois a reversão |

## Status HTTP

| Situação | Status |
|---|---|
| Processada | 201 (replay: 200) |
| Pendente de referência | 202 |
| Rejeitada por regra de negócio | 422 |
| Payload inválido | 400 |
| Conflito de idempotência ou carteira duplicada | 409 |
| Não encontrado | 404 |
| Banco indisponível | 503 |

## Endpoints

| Método | Caminho |
|---|---|
| POST | /api/wallets |
| GET | /api/wallets/{id} |
| GET | /api/wallets/{id}/ledger?page=&size= |
| POST | /api/wallets/{id}/reconciliation |
| POST | /api/wagering/transactions |
| GET | /api/wagering/transactions/{id} |
| GET | /health/live e /health/ready |

## Como rodar

```bash
docker-compose up -d        # sobe o PostgreSQL na porta 5434
./mvnw spring-boot:run      # sobe a aplicação (Flyway cria as tabelas)
./mvnw test                 # roda os testes (usam o banco do docker-compose)
```

No Windows use `mvnw.cmd` no lugar de `./mvnw`.
Se o banco foi criado antes do Flyway: `docker-compose down -v` e subir de novo.

## Testes

- `WageringApplicationTests`: BET, WIN, LOSS, REFUND, ROLLBACK, saldo insuficiente, idempotência,
  conflito de payload, referência pendente e reconciliação.
- `ConcurrencyTests`: threads em paralelo no PostgreSQL real. Duas apostas de 80 sobre saldo 100
  (uma processada, uma rejeitada, saldo 20, um débito) e a mesma aposta 50 vezes (um débito).
- Os testes rodam no mesmo banco da aplicação e apagam os dados no início.

## Limitações conhecidas

- Publisher da outbox, consumer SQS, inbox e DLQ não foram feitos. Os eventos ficam só na tabela.
- Sem métricas, sem `correlationId` nos logs e sem logs em JSON.
- Sem autenticação. Com mais tempo eu integraria um Identity Provider (ex.: Keycloak com OIDC).
- A chave de idempotência vem no body (`referenceId`), não no header `Idempotency-Key`, e não uso hash do payload.
- Não há `providerId`, `roundId` nem `gameId` no contrato.
- O ledger não tem trigger no banco; a imutabilidade é só por código (a aplicação nunca atualiza nem apaga).
- As migrations do Flyway não têm rollback (o desfazer seria uma migration nova).
- O retry de referência tem intervalo fixo de 10s, sem backoff exponencial.
- Paginação do ledger por `page`/`size`, não por cursor.
- Sem status FAILED. Erro de infraestrutura vira 503.
- Sem Testcontainers, sem teste com várias instâncias e sem teste de carga.
- Prefixo `/api` nos caminhos, diferente do enunciado.

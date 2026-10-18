# Jungle Wagering Processor

API em Java que processa transações de apostas (BET, WIN, LOSS, REFUND e ROLLBACK),
mantendo o saldo da carteira sempre batendo com o ledger (livro de lançamentos),
mesmo quando a mesma requisição chega duplicada ou várias chegam ao mesmo tempo.

Feito para o desafio técnico da Jungle Gaming. As decisões estão no [ARCHITECTURE.md](ARCHITECTURE.md).

## Tecnologias

- Java 21
- Spring Boot 4 (Web, Validation, Data JPA)
- PostgreSQL 15 (via Docker)
- Flyway (migrations)
- Maven e Lombok

## O que precisa ter instalado

- Java 21
- Docker Desktop

## Como rodar

1. Subir o banco de dados:

```bash
docker-compose up -d
```

O PostgreSQL sobe na porta **5434** (banco `wagering_db`, usuário `postgres`).

2. Subir a aplicação:

```bash
./mvnw spring-boot:run
```

No Windows (CMD ou PowerShell): `mvnw.cmd spring-boot:run`

As tabelas são criadas sozinhas pelo Flyway na primeira vez. A API fica em `http://localhost:8080`.

3. Rodar os testes (o banco do docker-compose precisa estar de pé):

```bash
./mvnw test
```

Os testes apagam os dados do banco no início de cada teste.

## Se der erro de banco

Se o banco já tinha tabelas de antes, o Flyway reclama. Para começar do zero:

```bash
docker-compose down -v
docker-compose up -d
```

## Endpoints

| Método | Caminho | O que faz |
|---|---|---|
| POST | /api/wallets | cria uma carteira |
| GET | /api/wallets/{id} | consulta a carteira |
| GET | /api/wallets/{id}/ledger?page=0&size=50 | lista os lançamentos |
| POST | /api/wallets/{id}/reconciliation | confere saldo x ledger |
| POST | /api/wagering/transactions | envia BET, WIN, LOSS, REFUND ou ROLLBACK |
| GET | /api/wagering/transactions/{id} | consulta uma transação |
| GET | /health/live | app está viva |
| GET | /health/ready | banco está acessível |

### Exemplos

Criar carteira:

```json
{
  "playerId": "player-1",
  "initialBalance": 100.00,
  "currency": "BRL"
}
```

Enviar uma aposta:

```json
{
  "walletId": "ID_DA_CARTEIRA",
  "type": "BET",
  "amount": 30.00,
  "currency": "BRL",
  "referenceId": "bet-001"
}
```

REFUND e ROLLBACK precisam do `relatedReferenceId` (o `referenceId` da transação original):

```json
{
  "walletId": "ID_DA_CARTEIRA",
  "type": "REFUND",
  "amount": 30.00,
  "currency": "BRL",
  "referenceId": "refund-001",
  "relatedReferenceId": "bet-001"
}
```

## Status HTTP

| Situação | Status |
|---|---|
| Processada | 201 (se for repetição: 200) |
| Pendente esperando a referência | 202 |
| Rejeitada por regra de negócio | 422 |
| Dados inválidos | 400 |
| Mesmo referenceId com dados diferentes | 409 |
| Não encontrado | 404 |
| Banco indisponível | 503 |

## O que não foi feito

Publicação da outbox no SQS, consumer, inbox, DLQ, métricas e autenticação ficaram de fora.
A lista completa está no final do [ARCHITECTURE.md](ARCHITECTURE.md).

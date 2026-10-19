# Simple Ledger

A simple in-memory ledger REST API built with Java 21 and Spring Boot. Record
deposits and withdrawals, retrieve the current balance, and view transaction
history for a single GBP ledger.

The ledger starts with a balance of `0.00` and an empty transaction history.

## Quick start

Requires **JDK 21**, with `JAVA_HOME` pointing to the JDK. The Maven Wrapper is
included; no separate Maven installation, database, or external service is needed.
The first build requires internet access to download Maven and dependencies.

```shell
./mvnw test
./mvnw spring-boot:run
```

These commands use macOS/Linux syntax. On Windows, replace `./mvnw` with
`.\mvnw.cmd`.

**Base URL:** `http://127.0.0.1:8086`. The address and port are configured in
[`application.yaml`](src/main/resources/application.yaml).

### Run as a JAR

Alternatively, package the application (including running the tests) and start
the executable JAR:

```shell
./mvnw clean package
java -jar target/simple-ledger-0.0.1-SNAPSHOT.jar
```

## API

All endpoints return JSON. Transaction requests must use
`Content-Type: application/json`.

| Method | Path | Description | Success status |
|---|---|---|---|
| `POST` | `/ledger/transactions` | Record a deposit or withdrawal | `201 Created` |
| `GET` | `/ledger/balance` | Retrieve the current GBP balance | `200 OK` |
| `GET` | `/ledger/transactions` | Retrieve transactions in insertion order | `200 OK` |

Record a deposit:

```shell
curl -i http://127.0.0.1:8086/ledger/transactions \
  -H 'Content-Type: application/json' \
  -d '{"type":"DEPOSIT","amount":"100.00"}'
```

Example response for the first transaction:

```json
{
  "id": 1,
  "type": "DEPOSIT",
  "amount": "100.00",
  "currency": "GBP",
  "recordedAt": "2026-10-19T12:00:00Z"
}
```

The application generates `recordedAt` in UTC. The timestamp above is
illustrative. To withdraw funds, use `"type":"WITHDRAWAL"` with the amount to
withdraw.

Retrieve the balance:

```shell
curl -i http://127.0.0.1:8086/ledger/balance
```

After the deposit above, the response is
`{"currency":"GBP","balance":"100.00"}`.

Retrieve transaction history:

```shell
curl -i http://127.0.0.1:8086/ledger/transactions
```

The response is an array of transaction objects in insertion order, or `[]` for
an empty ledger.

See [`api-requests.http`](api-requests.http) for a complete walkthrough, including
withdrawals and error cases. Run the requests from top to bottom against a fresh
application instance; repeating successful POSTs records additional transactions.

## Validation and errors

### Request format

| Field | Rules |
|---|---|
| `type` | Required JSON string: exactly `DEPOSIT` or `WITHDRAWAL` (case-sensitive). |
| `amount` | Required JSON string: digits with an optional one or two digit fractional part; at most 32 characters in total. |

Accepted amount formats include `"10"`, `"10.5"`, and `"10.50"`. Numeric JSON
values, signs, whitespace, and scientific notation are rejected.

The 32-character limit is a technical bound on numeric input size, not a
business-defined maximum transaction amount.

### Ledger rules

- Amounts must be greater than zero and are normalised to two decimal places
  without rounding.
- Withdrawals may equal the current balance but cannot exceed it.
- Rejected transactions leave the balance and history unchanged.

Transaction validation and insufficient-funds errors use this response structure:

```json
{
  "code": "INVALID_TRANSACTION",
  "message": "Invalid transaction request"
}
```

| HTTP status | Code | Message | Cause |
|---|---|---|---|
| `400 Bad Request` | `INVALID_TRANSACTION` | Invalid transaction request | Malformed JSON, missing or invalid fields, or a non-positive amount. |
| `409 Conflict` | `INSUFFICIENT_FUNDS` | Insufficient funds | A withdrawal exceeds the current balance. |

## Design decisions

Controllers handle HTTP requests and map API DTOs; the service owns business
rules and in-memory state.

| Decision | Rationale and trade-off |
|---|---|
| `BigDecimal` and string amounts | Exact decimal arithmetic avoids binary floating-point errors. API amounts are strings, returned with two decimal places. |
| Balance derived from history | No separate balance to keep in sync. Balance reads and withdrawals are O(n); deposits are amortised O(1), where n is the number of transactions. |
| One lock per service instance | Protects the sufficient-funds check, ID generation, insertion, and reads, preventing concurrent overdrafts. State operations run one at a time; input validation happens outside the lock. |
| Immutable history snapshots | O(n) list copies contain immutable transaction values. Callers cannot modify a snapshot, and later writes do not change it. |
| Injectable `Clock` | Provides UTC timestamps and deterministic tests. History follows insertion order, not timestamp order. |

## Testing

Run all tests with `./mvnw test`. The test suite includes:

- **Service tests:** deposits, withdrawals, exact decimal arithmetic, amount
  validation, insufficient funds, transaction IDs, fixed timestamps, and
  immutable history snapshots.
- **Concurrency tests:** concurrent deposits retain all updates, and competing
  withdrawals cannot overdraw the ledger.
- **HTTP integration tests:** verify API responses and input validation, and confirm 
  that rejected requests do not change the balance or transaction history.

## Scope and limitations

- A single GBP ledger; no account management or other currencies.
- Unbounded, process-local storage with no persistence or pagination. All data
  is lost on restart, and separate application instances do not share state.
- Transaction IDs are sequential within one service instance, starting at `1`.
  They reset on restart and are not globally unique.
- No authentication or idempotency mechanism. Each successful POST creates a
  transaction, including retries.

The default configuration binds only to the local loopback address. Durable
storage, access control, and request idempotency would need to be addressed before
broader deployment.

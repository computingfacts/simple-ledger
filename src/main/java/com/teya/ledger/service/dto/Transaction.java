package com.teya.ledger.service.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * An immutable record of a deposit or withdrawal.
 *
 * @author Joseph Sampson
 */
public record Transaction(
    long id,
    TransactionType type,
    BigDecimal amount,
    Instant recordedAt) {

}

package com.teya.ledger.service.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * @author Joseph Sampson
 */
public record Transaction(
    long id,
    TransactionType type,
    BigDecimal amount,
    Instant recordedAt) {

}

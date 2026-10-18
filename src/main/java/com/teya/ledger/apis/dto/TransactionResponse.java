package com.teya.ledger.apis.dto;

import com.teya.ledger.service.dto.Transaction;
import com.teya.ledger.service.dto.TransactionType;
import java.time.Instant;

/**
 * @author Joseph Sampson
 */
public record TransactionResponse(long id, TransactionType type, String amount, String currency, Instant recordedAt) {

  public static TransactionResponse from(Transaction transaction) {
    return new TransactionResponse(
        transaction.id(),
        transaction.type(),
        transaction.amount().toPlainString(),
        "GBP",
        transaction.recordedAt());
  }
}

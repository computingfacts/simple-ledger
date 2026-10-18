package com.teya.ledger.apis.dto;

import java.math.BigDecimal;

/**
 *
 * @author Joseph Sampson
 */
public record BalanceResponse(String currency, String balance) {

  public static BalanceResponse from(BigDecimal balance) {
    return new BalanceResponse("GBP", balance.toPlainString());
  }
}
package com.teya.ledger.service.exceptions;

/**
 * @author Joseph Sampson
 */
public class InsufficientFundsException extends RuntimeException {
  public InsufficientFundsException(String message) {
    super(message);
  }

}

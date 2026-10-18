package com.teya.ledger.service.exceptions;

/**
 * @author Joseph Sampson
 */
public class InvalidTransactionException extends RuntimeException {
  public InvalidTransactionException(String message) {
    super(message);
  }
}

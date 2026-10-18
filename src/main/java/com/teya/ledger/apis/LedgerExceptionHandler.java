package com.teya.ledger.apis;

import com.teya.ledger.apis.dto.ErrorResponse;
import com.teya.ledger.service.exceptions.InsufficientFundsException;
import com.teya.ledger.service.exceptions.InvalidTransactionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * @author Joseph Sampson
 */
@RestControllerAdvice
public class LedgerExceptionHandler {

  @ExceptionHandler({
      InvalidTransactionException.class,
      HttpMessageNotReadableException.class,
      MethodArgumentNotValidException.class
  })
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ErrorResponse invalidTransaction() {
    return new ErrorResponse("INVALID_TRANSACTION", "Invalid transaction request");
  }

  @ExceptionHandler(InsufficientFundsException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public ErrorResponse insufficientFunds() {
    return new ErrorResponse("INSUFFICIENT_FUNDS", "Insufficient funds");
  }
}

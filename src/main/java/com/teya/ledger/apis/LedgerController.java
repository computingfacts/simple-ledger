package com.teya.ledger.apis;

import com.teya.ledger.apis.dto.BalanceResponse;
import com.teya.ledger.apis.dto.TransactionRequest;
import com.teya.ledger.apis.dto.TransactionResponse;
import com.teya.ledger.service.LedgerService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Joseph Sampson
 */
@RestController
@RequestMapping(value = "/ledger", produces = MediaType.APPLICATION_JSON_VALUE)
public class LedgerController {

  private final LedgerService ledgerService;

  public LedgerController(LedgerService ledgerService) {
    this.ledgerService = ledgerService;
  }

  @PostMapping(value = "/transactions", consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public TransactionResponse recordTransaction(@Valid @RequestBody TransactionRequest request) {
    var amount = new BigDecimal(request.amount());
    var transaction = ledgerService.recordTransaction(request.type(), amount);
    return TransactionResponse.from(transaction);
  }

  @GetMapping("/balance")
  public BalanceResponse getBalance() {
    return BalanceResponse.from(ledgerService.getBalance());
  }

  @GetMapping("/transactions")
  public List<TransactionResponse> getTransactions() {
    return ledgerService.getTransactions()
        .stream()
        .map(TransactionResponse::from)
        .toList();
  }
}

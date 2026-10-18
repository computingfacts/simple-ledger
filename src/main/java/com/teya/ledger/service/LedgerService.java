package com.teya.ledger.service;

import com.teya.ledger.service.dto.Transaction;
import com.teya.ledger.service.dto.TransactionType;
import com.teya.ledger.service.exceptions.InsufficientFundsException;
import com.teya.ledger.service.exceptions.InvalidTransactionException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * @author Joseph Sampson
 */
@Service
public class LedgerService {

  private static final BigDecimal ZERO_BALANCE = new BigDecimal("0.00");
  private static final int SCALE = 2;

  private final Clock clock;
  private final Object lock = new Object();
  private final List<Transaction> transactions = new ArrayList<>();

  public LedgerService(Clock clock) {
    this.clock = Objects.requireNonNull(clock);
  }

  public Transaction recordTransaction(TransactionType type, BigDecimal amount) {
    var normalizedAmount = validateAndNormalizeAmount(type, amount);

    synchronized (lock) {
      var balance = calculateBalance();
      if (type == TransactionType.WITHDRAWAL && normalizedAmount.compareTo(balance) > 0) {
        throw new InsufficientFundsException(
            "Cannot withdraw %s: the balance is only %s".formatted(normalizedAmount.toPlainString(),
                balance.toPlainString()));
      }
      long transactionId = transactions.size();
      var transaction = new Transaction(transactionId, type, normalizedAmount, clock.instant());
      transactions.add(transaction);
      return transaction;
    }
  }

  public BigDecimal getBalance() {
    synchronized (lock) {
      return calculateBalance();
    }
  }

  public List<Transaction> getTransactions() {
    synchronized (lock) {
      return List.copyOf(transactions);
    }
  }

  private static BigDecimal validateAndNormalizeAmount(TransactionType type, BigDecimal amount) {
    if (type == null || amount == null || amount.signum() <= 0 || amount.scale() > SCALE) {
      throw new InvalidTransactionException("Invalid transaction");
    }
    return amount.setScale(SCALE, RoundingMode.UNNECESSARY);
  }

  private BigDecimal calculateBalance() {
    BigDecimal balance = ZERO_BALANCE;
    for (Transaction transaction : transactions) {
      balance = switch (transaction.type()) {
        case DEPOSIT -> balance.add(transaction.amount());
        case WITHDRAWAL -> balance.subtract(transaction.amount());
      };
    }
    return balance;
  }
}

package com.teya.ledger.service;

import static com.teya.ledger.service.dto.TransactionType.DEPOSIT;
import static com.teya.ledger.service.dto.TransactionType.WITHDRAWAL;
import static org.assertj.core.api.Assertions.assertThat;

import com.teya.ledger.service.dto.Transaction;
import com.teya.ledger.service.exceptions.InsufficientFundsException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

/**
 * Tests ledger updates from multiple threads.
 *
 * @author Joseph Sampson
 */
class LedgerServiceConcurrencyTest {

  private static final int TIMEOUT_SECONDS = 5;

  private final LedgerService ledger =
      new LedgerService(Clock.fixed(Instant.parse("2026-10-18T12:00:00Z"), ZoneOffset.UTC));

  @Test
  void recordsAllConcurrentDeposits() throws InterruptedException, ExecutionException, TimeoutException {
    int depositorCount = 4;
    int depositsPerDepositor = 25;
    var depositorsReady = new CountDownLatch(depositorCount);
    var startSignal = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(depositorCount);

    try {
      var depositTasks = new ArrayList<Future<?>>();
      for (int depositor = 0; depositor < depositorCount; depositor++) {
        depositTasks.add(executor.submit(() -> {
          depositorsReady.countDown();
          if (!startSignal.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new AssertionError("Deposit start was not released");
          }
          for (int deposit = 0; deposit < depositsPerDepositor; deposit++) {
            ledger.recordTransaction(DEPOSIT, BigDecimal.ONE);
          }
          return null;
        }));
      }
      assertThat(depositorsReady.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
      startSignal.countDown();
      for (Future<?> depositTask : depositTasks) {
        depositTask.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
      }
    } finally {
      startSignal.countDown();
      executor.shutdownNow();
      assertThat(executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS))
          .isTrue();
    }

    assertThat(ledger.getBalance()).isEqualTo(new BigDecimal("100.00"));
    var history = ledger.getTransactions();
    assertThat(history).hasSize(100);
    for (int index = 0; index < 100; index++) {
      assertThat(history.get(index).id()).isEqualTo(index + 1L);
    }
  }

  @Test
  void preventsConcurrentOverdrafts() throws InterruptedException, ExecutionException, TimeoutException {
    ledger.recordTransaction(DEPOSIT, new BigDecimal("100.00"));
    int withdrawerCount = 2;
    var withdrawersReady = new CountDownLatch(withdrawerCount);
    var startSignal = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(withdrawerCount);

    try {
      var withdrawalResults = new ArrayList<Future<Boolean>>();
      for (int withdrawer = 0; withdrawer < withdrawerCount; withdrawer++) {
        withdrawalResults.add(executor.submit(() -> {
          withdrawersReady.countDown();
          if (!startSignal.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new AssertionError("Withdrawal start was not released");
          }
          try {
            ledger.recordTransaction(WITHDRAWAL, new BigDecimal("80.00"));
            return true;
          } catch (InsufficientFundsException expected) {
            return false;
          }
        }));
      }
      assertThat(withdrawersReady.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
      startSignal.countDown();

      boolean firstWithdrawalSucceeded = withdrawalResults.get(0).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
      boolean secondWithdrawalSucceeded = withdrawalResults.get(1).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
      assertThat(List.of(firstWithdrawalSucceeded, secondWithdrawalSucceeded))
          .containsExactlyInAnyOrder(true, false);
    } finally {
      startSignal.countDown();
      executor.shutdownNow();
      assertThat(executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS))
          .isTrue();
    }

    assertThat(ledger.getBalance()).isEqualTo(new BigDecimal("20.00"));
    assertThat(ledger.getTransactions()).extracting(Transaction::type).containsExactly(DEPOSIT, WITHDRAWAL);

    var deposit = ledger.recordTransaction(DEPOSIT, BigDecimal.ONE);
    assertThat(deposit.id()).isEqualTo(3);
  }
}

package com.teya.ledger.service;

import static com.teya.ledger.service.dto.TransactionType.DEPOSIT;
import static com.teya.ledger.service.dto.TransactionType.WITHDRAWAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.teya.ledger.service.dto.Transaction;
import com.teya.ledger.service.dto.TransactionType;
import com.teya.ledger.service.exceptions.InsufficientFundsException;
import com.teya.ledger.service.exceptions.InvalidTransactionException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests deposits, withdrawals and transaction history.
 *
 * @author Joseph Sampson
 */
class LedgerServiceTest {

  private static final Instant TIME = Instant.parse("2026-10-18T12:00:00Z");
  private final LedgerService ledger = new LedgerService(Clock.fixed(TIME, ZoneOffset.UTC));

  @Test
  void startsWithAnEmptyLedger() {
    assertThat(ledger.getBalance()).isEqualTo(amount("0.00"));
    assertThat(ledger.getTransactions()).isEmpty();
  }

  @Test
  void recordsDepositsAndWithdrawals() {
    var deposit = ledger.recordTransaction(DEPOSIT, amount("100"));
    var withdrawal = ledger.recordTransaction(WITHDRAWAL, amount("25.0"));
    var secondDeposit = ledger.recordTransaction(DEPOSIT, amount("5.00"));

    assertThat(ledger.getBalance()).isEqualTo(amount("80.00"));
    assertThat(deposit).isEqualTo(new Transaction(1, DEPOSIT, amount("100.00"), TIME));
    assertThat(withdrawal).isEqualTo(new Transaction(2, WITHDRAWAL, amount("25.00"), TIME));
    assertThat(secondDeposit.id()).isEqualTo(3);
    assertThat(ledger.getTransactions()).containsExactly(deposit, withdrawal, secondDeposit);
  }

  @Test
  void calculatesBalanceUsingExactDecimalArithmetic() {
    ledger.recordTransaction(DEPOSIT, amount("0.10"));
    ledger.recordTransaction(DEPOSIT, amount("0.20"));

    assertThat(ledger.getBalance()).isEqualTo(amount("0.30"));
  }

  @Test
  void allowsWithdrawingTheWholeBalance() {
    ledger.recordTransaction(DEPOSIT, amount("0.10"));
    ledger.recordTransaction(DEPOSIT, amount("0.20"));

    ledger.recordTransaction(WITHDRAWAL, amount("0.30"));

    assertThat(ledger.getBalance()).isEqualTo(amount("0.00"));
    assertThat(ledger.getTransactions()).hasSize(3);
  }

  @Test
  void rejectsZeroDepositWithoutChangingAnEmptyLedger() {
    BigDecimal zeroAmount = amount("0.00");

    assertThatThrownBy(() -> ledger.recordTransaction(DEPOSIT, zeroAmount))
        .isInstanceOf(InvalidTransactionException.class)
        .hasMessage("Invalid transaction request");

    assertThat(ledger.getBalance()).isEqualTo(amount("0.00"));
    assertThat(ledger.getTransactions()).isEmpty();

    var deposit = ledger.recordTransaction(DEPOSIT, amount("1.00"));
    assertThat(deposit.id()).isEqualTo(1);
  }

  @Test
  void returnsAnUnmodifiableHistorySnapshot() {
    var firstDeposit = ledger.recordTransaction(DEPOSIT, amount("1.00"));
    var historySnapshot = ledger.getTransactions();

    assertThatThrownBy(historySnapshot::clear).isInstanceOf(UnsupportedOperationException.class);

    ledger.recordTransaction(DEPOSIT, amount("1.00"));

    assertThat(historySnapshot).containsExactly(firstDeposit);
    assertThat(ledger.getTransactions()).hasSize(2);
  }

  @Test
  void returnsHistoryInInsertionOrderEvenWhenTheClockMovesBackwards() {
    Clock backwardsMovingClock = mock(Clock.class);
    when(backwardsMovingClock.instant()).thenReturn(TIME, TIME.minusSeconds(60));
    var ledgerWithBackwardsMovingClock = new LedgerService(backwardsMovingClock);

    var firstDeposit = ledgerWithBackwardsMovingClock.recordTransaction(DEPOSIT, amount("1.00"));
    var secondDeposit = ledgerWithBackwardsMovingClock.recordTransaction(DEPOSIT, amount("1.00"));

    assertThat(ledgerWithBackwardsMovingClock.getTransactions())
        .containsExactly(firstDeposit, secondDeposit);
    assertThat(firstDeposit.recordedAt()).isEqualTo(TIME);
    assertThat(secondDeposit.recordedAt()).isBefore(firstDeposit.recordedAt());
  }

  @Test
  void newServiceInstanceStartsWithAnEmptyLedger() {
    ledger.recordTransaction(DEPOSIT, amount("10.00"));

    var newLedger = new LedgerService(Clock.fixed(TIME, ZoneOffset.UTC));

    assertThat(newLedger.getTransactions()).isEmpty();
    assertThat(newLedger.getBalance()).isEqualTo(amount("0.00"));
  }

  @Test
  void rejectsWithdrawalsAboveTheBalance() {
    ledger.recordTransaction(DEPOSIT, amount("100.00"));
    var historyBeforeRejection = ledger.getTransactions();
    BigDecimal withdrawalAmount = amount("100.01");

    assertThatThrownBy(() -> ledger.recordTransaction(WITHDRAWAL, withdrawalAmount))
        .isInstanceOf(InsufficientFundsException.class)
        .hasMessage("Cannot withdraw 100.01: the balance is only 100.00");

    assertThat(ledger.getBalance()).isEqualTo(amount("100.00"));
    assertThat(ledger.getTransactions()).isEqualTo(historyBeforeRejection);

    var withdrawal = ledger.recordTransaction(WITHDRAWAL, amount("100.00"));
    assertThat(withdrawal.id()).isEqualTo(2);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidTransactions")
  void rejectsInvalidTransactionsWithoutChangingTheLedger(
      String description, TransactionType type, BigDecimal invalidAmount) {
    var deposit = ledger.recordTransaction(DEPOSIT, amount("25.00"));

    assertThatThrownBy(() -> ledger.recordTransaction(type, invalidAmount))
        .as(description)
        .isInstanceOf(InvalidTransactionException.class)
        .hasMessage("Invalid transaction request");

    assertThat(ledger.getTransactions()).containsExactly(deposit);
    assertThat(ledger.getBalance()).isEqualTo(amount("25.00"));

    var withdrawal = ledger.recordTransaction(WITHDRAWAL, amount("1.00"));
    assertThat(withdrawal.id()).isEqualTo(2);
  }

  static Stream<Arguments> invalidTransactions() {
    return Stream.of(
        Arguments.of("missing type", null, amount("1.00")),
        Arguments.of("missing deposit amount", DEPOSIT, null),
        Arguments.of("missing withdrawal amount", WITHDRAWAL, null),
        Arguments.of("zero deposit", DEPOSIT, amount("0")),
        Arguments.of("zero withdrawal", WITHDRAWAL, amount("0.00")),
        Arguments.of("negative deposit", DEPOSIT, amount("-1")),
        Arguments.of("negative withdrawal", WITHDRAWAL, amount("-1")),
        Arguments.of("deposit with excess precision", DEPOSIT, amount("1.001")),
        Arguments.of("withdrawal with excess precision", WITHDRAWAL, amount("1.001")),
        Arguments.of("deposit with a trailing fractional zero", DEPOSIT, amount("1.230")),
        Arguments.of("withdrawal with a trailing fractional zero", WITHDRAWAL, amount("1.230")));
  }

  @Test
  void rejectsWithdrawalsFromAnEmptyLedger() {
    BigDecimal withdrawalAmount = amount("1.00");

    assertThatThrownBy(() -> ledger.recordTransaction(WITHDRAWAL, withdrawalAmount))
        .isInstanceOf(InsufficientFundsException.class);

    assertThat(ledger.getTransactions()).isEmpty();
    assertThat(ledger.getBalance()).isEqualTo(amount("0.00"));

    var deposit = ledger.recordTransaction(DEPOSIT, amount("1.00"));
    assertThat(deposit.id()).isEqualTo(1);
  }

  @Test
  void leavesTheLedgerUnchangedWhenTheClockFails() {
    Clock failingClock = mock(Clock.class);
    when(failingClock.instant())
        .thenReturn(TIME)
        .thenThrow(new DateTimeException("Clock unavailable"))
        .thenReturn(TIME);
    var ledgerWithFailingClock = new LedgerService(failingClock);
    var deposit = ledgerWithFailingClock.recordTransaction(DEPOSIT, amount("10.00"));
    BigDecimal withdrawalAmount = amount("1.00");

    assertThatThrownBy(() -> ledgerWithFailingClock.recordTransaction(WITHDRAWAL, withdrawalAmount))
        .isInstanceOf(DateTimeException.class);

    assertThat(ledgerWithFailingClock.getTransactions()).containsExactly(deposit);
    assertThat(ledgerWithFailingClock.getBalance()).isEqualTo(amount("10.00"));

    var withdrawal = ledgerWithFailingClock.recordTransaction(WITHDRAWAL, amount("1.00"));
    assertThat(withdrawal.id()).isEqualTo(2);
  }

  private static BigDecimal amount(String value) {
    return new BigDecimal(value);
  }
}

package com.teya.ledger.apis;

import static com.teya.ledger.service.dto.TransactionType.DEPOSIT;
import static com.teya.ledger.service.dto.TransactionType.WITHDRAWAL;
import static org.assertj.core.api.Assertions.assertThat;

import com.teya.ledger.apis.dto.BalanceResponse;
import com.teya.ledger.apis.dto.ErrorResponse;
import com.teya.ledger.apis.dto.TransactionRequest;
import com.teya.ledger.apis.dto.TransactionResponse;
import com.teya.ledger.service.dto.TransactionType;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.client.RestClient;

/**
 * Tests the ledger API over HTTP.
 *
 * @author Joseph Sampson
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class LedgerHttpTest {

  private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(5);

  private final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build();
  private final RestClient client;

  @Autowired
  LedgerHttpTest(Environment environment) {
    var requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(HTTP_TIMEOUT);
    client = RestClient.builder()
        .baseUrl("http://127.0.0.1:" + environment.getRequiredProperty("local.server.port") + "/ledger")
        .requestFactory(requestFactory)
        .build();
  }

  @AfterEach
  void closeClient() {
    httpClient.close();
  }

  @Test
  void configuresLoopbackAddress(@Autowired Environment environment) {
    assertThat(environment.getProperty("server.address")).isEqualTo("127.0.0.1");
  }

  @Test
  void newLedgerHasZeroBalanceAndNoTransactions() {
    assertThat(getBalance()).isEqualTo(new BalanceResponse("GBP", "0.00"));
    assertThat(getTransactions()).isEmpty();
  }

  @Test
  void depositAndWithdrawalAppearInHistory() {
    var deposit = recordTransaction(DEPOSIT, "100");
    var withdrawal = recordTransaction(WITHDRAWAL, "25.0");

    assertThat(deposit.id()).isEqualTo(1);
    assertThat(deposit.type()).isEqualTo(DEPOSIT);
    assertThat(deposit.amount()).isEqualTo("100.00");
    assertThat(deposit.currency()).isEqualTo("GBP");
    assertThat(deposit.recordedAt()).isNotNull();
    assertThat(withdrawal.id()).isEqualTo(2);
    assertThat(withdrawal.type()).isEqualTo(WITHDRAWAL);
    assertThat(withdrawal.amount()).isEqualTo("25.00");
    assertThat(withdrawal.currency()).isEqualTo("GBP");
    assertThat(withdrawal.recordedAt()).isNotNull();
    assertThat(getBalance()).isEqualTo(new BalanceResponse("GBP", "75.00"));
    assertThat(getTransactions()).containsExactly(deposit, withdrawal);
  }

  @Test
  void calculatesExactDecimalBalanceAndAllowsFullWithdrawal() {
    recordTransaction(DEPOSIT, "0.10");
    recordTransaction(DEPOSIT, "0.20");
    assertThat(getBalance()).isEqualTo(new BalanceResponse("GBP", "0.30"));

    recordTransaction(WITHDRAWAL, "0.30");
    assertThat(getBalance()).isEqualTo(new BalanceResponse("GBP", "0.00"));
    assertThat(getTransactions()).hasSize(3);
  }

  @Test
  void rejectsWithdrawalAboveTheBalanceWithoutChangingTheLedger() {
    var deposit = recordTransaction(DEPOSIT, "25.00");

    var response = post(new TransactionRequest(WITHDRAWAL, "30.00"), ErrorResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody()).isEqualTo(new ErrorResponse("INSUFFICIENT_FUNDS", "Insufficient funds"));
    assertThat(getBalance()).isEqualTo(new BalanceResponse("GBP", "25.00"));
    assertThat(getTransactions()).containsExactly(deposit);
    assertThat(recordTransaction(WITHDRAWAL, "25.00").id()).isEqualTo(2);
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "", "0", "0.00", "-1", "+1", "1.001", "1.230", "1e2", " 1", "1 ", ".25", "1.", "NaN", "١"
  })
  void rejectsInvalidAmounts(String amount) {
    assertRejectedWithoutChangingTheLedger(new TransactionRequest(DEPOSIT, amount));
  }

  @ParameterizedTest
  @MethodSource("missingOrInvalidFields")
  void rejectsMissingOrInvalidFields(Object request) {
    assertRejectedWithoutChangingTheLedger(request);
  }

  static Stream<Object> missingOrInvalidFields() {
    return Stream.of(
        Map.of(),
        Map.of("type", "DEPOSIT"),
        Map.of("amount", "1.00"),
        new TransactionRequest(null, "1.00"),
        new TransactionRequest(DEPOSIT, null),
        new TransactionRequest(DEPOSIT, "1".repeat(33)));
  }

  @ParameterizedTest
  @MethodSource("incorrectJsonValueTypes")
  void rejectsIncorrectJsonValueTypes(Object request) {
    assertRejectedWithoutChangingTheLedger(request);
  }

  static Stream<Object> incorrectJsonValueTypes() {
    return Stream.of(
        Map.of("type", 0, "amount", "1.00"),
        Map.of("type", 1, "amount", "1.00"),
        Map.of("type", true, "amount", "1.00"),
        Map.of("type", List.of("DEPOSIT"), "amount", "1.00"),
        Map.of("type", Map.of(), "amount", "1.00"),
        Map.of("type", "DEPOSIT", "amount", 100.00),
        Map.of("type", "DEPOSIT", "amount", true),
        Map.of("type", "DEPOSIT", "amount", List.of()),
        Map.of("type", "DEPOSIT", "amount", Map.of()));
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "",
      "deposit",
      "withdrawal",
      "TRANSFER",
      "<script>malicious code</script>",
      "0",
      "1",
      " DEPOSIT",
      "DEPOSIT ",
      "WITHDRAWAL\n"
  })
  void rejectsUnsupportedTransactionTypeStrings(String type) {
    assertRejectedWithoutChangingTheLedger(Map.of("type", type, "amount", "1.00"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "null", "[]", "\"text\"", "{"})
  void rejectsMalformedOrNonObjectJson(String rawJson) {
    assertRejectedWithoutChangingTheLedger(rawJson);
  }

  @Test
  void acceptsAnAmountAtThe32CharacterLimit() {
    String amount = "1".repeat(29) + ".23";
    var transaction = recordTransaction(DEPOSIT, amount);
    assertThat(transaction.amount()).isEqualTo(amount);
    assertThat(getBalance()).isEqualTo(new BalanceResponse("GBP", amount));
  }

  @Test
  void returnsOnlyTheDocumentedBalanceAndTransactionFields() {
    recordTransaction(DEPOSIT, "1");
    var balance =
        client.get()
            .uri("/balance")
            .retrieve()
            .body(new ParameterizedTypeReference<Map<String, Object>>() {});

    var history = client.get()
        .uri("/transactions")
        .retrieve()
        .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});

    assertThat(balance).containsExactlyInAnyOrderEntriesOf(Map.of("currency", "GBP", "balance", "1.00"));
    assertThat(history).hasSize(1);
    assertThat(history.getFirst())
        .containsOnlyKeys("id", "type", "amount", "currency", "recordedAt")
        .containsEntry("amount", "1.00");
  }

  @Test
  void returnsOnlyTheDocumentedValidationErrorFields() {
    var error = client.post()
        .uri("/transactions")
        .contentType(MediaType.APPLICATION_JSON)
        .body(new TransactionRequest(DEPOSIT, "0"))
        .exchange((request, response) ->
            response.bodyTo(new ParameterizedTypeReference<Map<String, Object>>() {}));

    assertThat(error)
        .containsExactlyInAnyOrderEntriesOf(
            Map.of("code", "INVALID_TRANSACTION", "message", "Invalid transaction request"));
  }

  @Test
  void rejectsUnsupportedHttpMethodAndContentType() {
    var unsupportedMethodStatus = client.put()
        .uri("/transactions")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of())
        .exchange((request, response) -> response.getStatusCode());

    var unsupportedContentTypeStatus = client.post()
        .uri("/transactions")
        .contentType(MediaType.TEXT_PLAIN)
        .body("text")
        .exchange((request, response) -> response.getStatusCode());

    assertThat(unsupportedMethodStatus).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    assertThat(unsupportedContentTypeStatus).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    assertThat(getTransactions()).isEmpty();
  }

  private TransactionResponse recordTransaction(TransactionType type, String amount) {
    var response = post(new TransactionRequest(type, amount), TransactionResponse.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    assertThat(response.getBody()).isNotNull();
    return response.getBody();
  }

  private BalanceResponse getBalance() {
    var response = client.get().uri("/balance").retrieve().toEntity(BalanceResponse.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    return response.getBody();
  }

  private List<TransactionResponse> getTransactions() {
    var response = client.get().uri("/transactions").retrieve().toEntity(TransactionResponse[].class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    return List.of(response.getBody());
  }

  private void assertRejectedWithoutChangingTheLedger(Object request) {
    recordTransaction(DEPOSIT, "25.00");

    var historyBeforeRejection = getTransactions();
    var balanceBeforeRejection = getBalance();
    var response = post(request, ErrorResponse.class);
    assertThat(response.getStatusCode())
        .as("Status for invalid request: %s", request)
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    assertThat(response.getBody())
        .isEqualTo(new ErrorResponse("INVALID_TRANSACTION", "Invalid transaction request"));
    assertThat(getTransactions()).isEqualTo(historyBeforeRejection);
    assertThat(getBalance()).isEqualTo(balanceBeforeRejection);

    var nextTransaction = recordTransaction(DEPOSIT, "1.00");
    assertThat(nextTransaction.id()).isEqualTo(historyBeforeRejection.size() + 1L);
  }

  private <T> ResponseEntity<T> post(Object body, Class<T> responseType) {
    return client.post()
        .uri("/transactions")
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange((request, response) -> new ResponseEntity<>(
            response.bodyTo(responseType), response.getHeaders(), response.getStatusCode()));
  }
}

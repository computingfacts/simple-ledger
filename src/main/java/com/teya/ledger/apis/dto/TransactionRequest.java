package com.teya.ledger.apis.dto;

import com.teya.ledger.service.dto.TransactionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * Request to record a deposit or withdrawal.
 *
 * @author Joseph Sampson
 */
public record TransactionRequest(
    @NotNull @JsonDeserialize(using = TransactionTypeDeserializer.class)
    TransactionType type,

    @NotNull
    @Size(max = 32)
    @Pattern(regexp = "\\d+(?:\\.\\d{1,2})?")
    @JsonDeserialize(using = StrictStringDeserializer.class)
    String amount) {}

package com.teya.ledger.apis.dto;

import com.teya.ledger.service.dto.TransactionType;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * Accepts transaction type names exactly as defined in the enum.
 *
 * @author Joseph Sampson
 */
public class TransactionTypeDeserializer extends ValueDeserializer<TransactionType> {

  @Override
  public TransactionType deserialize(JsonParser parser, DeserializationContext context) {
    if (parser.hasToken(JsonToken.VALUE_STRING)) {
      var value = parser.getString();
      for (TransactionType type : TransactionType.values()) {
        if (type.name().equals(value)) {
          return type;
        }
      }
    }
    return context.reportInputMismatch(TransactionType.class, "Expected a supported transaction type string");
  }
}
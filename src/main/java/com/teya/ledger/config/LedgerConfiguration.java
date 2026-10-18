package com.teya.ledger.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides an injectable clock so recorded timestamps are deterministic in tests.
 *
 * @author Joseph Sampson
 */
@Configuration
public class LedgerConfiguration {

  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }
}

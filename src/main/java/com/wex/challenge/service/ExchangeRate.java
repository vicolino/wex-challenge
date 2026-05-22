package com.wex.challenge.service;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExchangeRate(String currency, BigDecimal rate, LocalDate effectiveDate) {
}

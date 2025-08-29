package com.split.ai.split.service.core.utils;

import io.split.engine.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyUtil {
    private MoneyUtil() {}

    public static Money toMoney(BigDecimal amount, int scale) {
        BigDecimal normalized = amount.setScale(scale, RoundingMode.UNNECESSARY);
        long minor = normalized.movePointRight(scale).longValueExact();
        return new Money(minor, scale);
    }

    public static BigDecimal toBig(Money money) {
        return BigDecimal.valueOf(money.getMinor()).movePointLeft(money.getScale());
    }
}

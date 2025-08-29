package com.split.ai.split.service.model.enums;

public enum CURRENCY {
    INR(2),
    USD(2),
    EUR(2);

    private final int scale;

    CURRENCY(int scale) {
        this.scale = scale;
    }

    public int scale() {
        return scale;
    }
}

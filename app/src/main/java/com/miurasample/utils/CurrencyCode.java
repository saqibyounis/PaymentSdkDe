/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasample.utils;

public enum CurrencyCode {

    GBP(826, "\u00a3"),
    USD(840, "\u0024"),
    EUR(978, "\u20ac"),
    PLN(985, "PLN");

    String euro = "\u20ac";

    String pound = "\u00a3";
    private int value;
    private String sign;

    CurrencyCode(int value, String sign) {
        this.value = value;
        this.sign = sign;
    }

    public int getValue() {
        return value;
    }

    public String getSign() {
        return sign;
    }
}

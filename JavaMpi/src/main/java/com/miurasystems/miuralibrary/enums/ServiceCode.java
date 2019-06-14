/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.enums;

import java.io.Serializable;

/**
 * Service code with enums for all digits. Read https://en.wikipedia.org/wiki/Magnetic_stripe_card for more information
 */
public class ServiceCode implements Serializable {

    private String serviceCode;

    private ServiceCodeFirstDigit firstDigit;
    private ServiceCodeSecondDigit secondDigit;
    private ServiceCodeThirdDigit thirdDigit;

    public ServiceCode(String serviceCode) {
        this.serviceCode = serviceCode;

        this.firstDigit = ServiceCodeFirstDigit.getByValue(Character.getNumericValue(serviceCode.charAt(0)));
        this.secondDigit = ServiceCodeSecondDigit.getByValue(Character.getNumericValue(serviceCode.charAt(1)));
        this.thirdDigit = ServiceCodeThirdDigit.getByValue(Character.getNumericValue(serviceCode.charAt(2)));
    }

    public String getServiceCode() {
        return serviceCode;
    }

    public ServiceCodeFirstDigit getFirstDigit() {
        return firstDigit;
    }

    public ServiceCodeSecondDigit getSecondDigit() {
        return secondDigit;
    }

    public ServiceCodeThirdDigit getThirdDigit() {
        return thirdDigit;
    }

    @Override
    public String toString() {
        return "ServiceCode{" +
                "serviceCode='" + serviceCode + '\'' +
                ", firstDigit=" + firstDigit +
                ", secondDigit=" + secondDigit +
                ", thirdDigit=" + thirdDigit +
                '}';
    }

    public enum ServiceCodeFirstDigit {

        InternationalInterchange(1),
        InternationalInterchange_UseIC(2),
        NationalInterchangeOnly(5),
        NationalInterchangeOnly_UseIC(6),
        NoInterchange(7),
        Test(9);

        private int value;

        ServiceCodeFirstDigit(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static ServiceCodeFirstDigit getByValue(int value) {
            for (ServiceCodeFirstDigit field : ServiceCodeFirstDigit.values()) {
                if (field.getValue() == value) {
                    return field;
                }
            }
            return null;
        }
    }

    public enum ServiceCodeSecondDigit {

        Normal(0),
        AuthorizedOnline(2),
        AuthorizedOnlineExceptBilateralAgreement(4);

        private int value;

        ServiceCodeSecondDigit(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static ServiceCodeSecondDigit getByValue(int value) {
            for (ServiceCodeSecondDigit field : ServiceCodeSecondDigit.values()) {
                if (field.getValue() == value) {
                    return field;
                }
            }
            return null;
        }
    }

    public enum ServiceCodeThirdDigit {

        NoRestrictions_PINRequired(0),
        NoRestrictions(1),
        GoodsAndServicesOnly_NoCash(2),
        ATMOnly_PINRequired(3),
        CashOnly(4),
        GoodsAndServicesOnly_NoCash_PINRequired(5),
        NoRestrictions_PINOptional(6),
        GoodsAndServicesOnly_NoCash_PINOptional(7);

        private int value;

        ServiceCodeThirdDigit(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static ServiceCodeThirdDigit getByValue(int value) {
            for (ServiceCodeThirdDigit field : ServiceCodeThirdDigit.values()) {
                if (field.getValue() == value) {
                    return field;
                }
            }
            return null;
        }
    }

}

/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.tlv;

import com.miurasystems.miuralibrary.enums.ServiceCode;

import java.io.Serializable;

/**
 * Class containing the data received on the track 2 of a mag swipe card
 */
public class Track2Data implements Serializable {

    private boolean isMasked;
    private String PAN, expirationDate;
    private ServiceCode serviceCode;
    private byte[] raw;

    public String getPAN() {
        return PAN;
    }

    public void setPAN(String PAN) {
        this.PAN = PAN;
    }

    public String getExpirationDate() {
        return expirationDate;
    }

    public void setExpirationDate(String expirationDate) {
        this.expirationDate = expirationDate;
    }

    public ServiceCode getServiceCode() {
        return serviceCode;
    }

    public void setServiceCode(ServiceCode serviceCode) {
        this.serviceCode = serviceCode;
    }

    public byte[] getRaw() {
        return raw;
    }

    public void setRaw(byte[] raw) {
        this.raw = raw;
    }

    public boolean isMasked() {
        return isMasked;
    }

    public void setIsMasked(boolean isMasked) {
        this.isMasked = isMasked;
    }

    @Override
    public String toString() {
        return "Track2Data{" +
                "PAN='" + PAN + '\'' +
                ", expirationDate='" + expirationDate + '\'' +
                ", serviceCode=" + serviceCode +
                ", isMasked=" + isMasked +
                '}';
    }
}

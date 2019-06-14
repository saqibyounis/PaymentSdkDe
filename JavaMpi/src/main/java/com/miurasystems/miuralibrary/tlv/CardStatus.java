/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.tlv;

import java.io.Serializable;

public class CardStatus implements Serializable{

    private boolean cardPresent, EMVCompatible, MSRDataAvailable, track1DataAvailable, track2DataAvailable, track3DataAvailable;

    public boolean isCardPresent() {
        return cardPresent;
    }

    public void setCardPresent(boolean cardPresent) {
        this.cardPresent = cardPresent;
    }

    public boolean isEMVCompatible() {
        return EMVCompatible;
    }

    public void setEMVCompatible(boolean EMVCompatible) {
        this.EMVCompatible = EMVCompatible;
    }

    public boolean isMSRDataAvailable() {
        return MSRDataAvailable;
    }

    public void setMSRDataAvailable(boolean MSRDataAvailable) {
        this.MSRDataAvailable = MSRDataAvailable;
    }

    public boolean isTrack1DataAvailable() {
        return track1DataAvailable;
    }

    public void setTrack1DataAvailable(boolean track1DataAvailable) {
        this.track1DataAvailable = track1DataAvailable;
    }

    public boolean isTrack2DataAvailable() {
        return track2DataAvailable;
    }

    public void setTrack2DataAvailable(boolean track2DataAvailable) {
        this.track2DataAvailable = track2DataAvailable;
    }

    public boolean isTrack3DataAvailable() {
        return track3DataAvailable;
    }

    public void setTrack3DataAvailable(boolean track3DataAvailable) {
        this.track3DataAvailable = track3DataAvailable;
    }

    @Override
    public String toString() {
        return "CardStatus{" +
                "cardPresent=" + cardPresent +
                ", EMVCompatible=" + EMVCompatible +
                ", MSRDataAvailable=" + MSRDataAvailable +
                ", track1DataAvailable=" + track1DataAvailable +
                ", track2DataAvailable=" + track2DataAvailable +
                ", track3DataAvailable=" + track3DataAvailable +
                '}';
    }
}

/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.tlv;

import android.support.annotation.NonNull;

import com.miurasystems.miuralibrary.CommandUtil;
import com.miurasystems.miuralibrary.enums.ServiceCode;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;

public class CardData {

    private static final Charset US_ASCII = Charset.forName("US-ASCII");

    private byte[] raw;
    private String answerToReset, sredData, sredKSN;
    private Track2Data maskedTrack2Data;
    private CardStatus cardStatus;
    private String plainTrack1Data;
    private Track2Data plainTrack2Data;

    public byte[] getRaw() {
        return raw;
    }

    public void setRaw(byte[] raw) {
        this.raw = raw;
    }

    public String getAnswerToReset() {
        return answerToReset;
    }

    public void setAnswerToReset(String answerToReset) {
        this.answerToReset = answerToReset;
    }

    public String getSredData() {
        return sredData;
    }

    public void setSredData(String sredData) {
        this.sredData = sredData;
    }

    public String getSredKSN() {
        return sredKSN;
    }

    public void setSredKSN(String sredKSN) {
        this.sredKSN = sredKSN;
    }

    public Track2Data getMaskedTrack2Data() {
        return maskedTrack2Data;
    }

    public void setMaskedTrack2Data(Track2Data maskedTrack2Data) {
        this.maskedTrack2Data = maskedTrack2Data;
    }

    public CardStatus getCardStatus() {
        return cardStatus;
    }

    public void setCardStatus(CardStatus cardStatus) {
        this.cardStatus = cardStatus;
    }

    @Override
    public String toString() {
        return "CardData{" +
                "cardStatus=" + cardStatus +
                ", maskedTrack2Data=" + maskedTrack2Data +
                ", sredKSN='" + sredKSN + '\'' +
                ", sredData='" + sredData + '\'' +
                ", answerToReset='" + answerToReset + '\'' +
                ", raw=" + Arrays.toString(raw) +
                '}';
    }

    public String getPlainTrack1Data() {
        return plainTrack1Data;
    }

    public Track2Data getPlainTrack2Data() {
        return plainTrack2Data;
    }

    @NonNull
    public static CardData valueOf(@NonNull TLVObject tlvObject) {
        List<TLVObject> cardStatusObjects = tlvObject.getConstrustedTLV();
        TLVObject tlvCardStatus = CommandUtil.firstMatch(
                cardStatusObjects,
                Description.Card_Status
        );

        TLVObject tlvAnswerToReset = CommandUtil.firstMatch(
                cardStatusObjects,
                Description.ICC_Answer_To_Reset
        );
        TLVObject tlvSredData = CommandUtil.firstMatch(cardStatusObjects, Description.SRED_Data);
        TLVObject tlvSredKsn = CommandUtil.firstMatch(cardStatusObjects, Description.SRED_KSN);
        TLVObject tlvMaskedTrack2Data = CommandUtil.firstMatch(
                cardStatusObjects,
                Description.Masked_Track_2
        );
        TLVObject tlvPlainTrack1Data = CommandUtil.firstMatch(
                cardStatusObjects,
                Description.Track_1
        );
        TLVObject tlvPlainTrack2Data = CommandUtil.firstMatch(
                cardStatusObjects,
                Description.Track_2
        );

        byte insertStatus = tlvCardStatus.getRawData()[0];
        byte swipeStatus = tlvCardStatus.getRawData()[1];

        CardStatus cardStatus = parseCardStatus(insertStatus, swipeStatus);
        Track2Data maskedTrack2Data = parseTrack2Data(tlvMaskedTrack2Data, true);

        CardData cardData = new CardData();
        cardData.setCardStatus(cardStatus);
        cardData.setRaw(tlvObject.getRawData());
        cardData.setMaskedTrack2Data(maskedTrack2Data);
        if (tlvAnswerToReset != null) {
            cardData.setAnswerToReset(tlvAnswerToReset.getData());
        }
        if (tlvSredData != null) {
            cardData.setSredData(tlvSredData.getData());
        }
        if (tlvSredKsn != null) {
            cardData.setSredKSN(tlvSredKsn.getData());
        }
        if (tlvPlainTrack1Data != null) {
            cardData.plainTrack1Data = new String(tlvPlainTrack1Data.getRawData(), US_ASCII);
        }
        if (tlvPlainTrack2Data != null) {
            cardData.plainTrack2Data = parseTrack2Data(tlvPlainTrack2Data, false);
        }

        return cardData;
    }

    private static Track2Data parseTrack2Data(TLVObject tlvTrack2Data, boolean isMasked) {
        Track2Data track2Data = new Track2Data();

        track2Data.setIsMasked(isMasked);

        if (tlvTrack2Data != null) {
            String track2String = new String(tlvTrack2Data.getRawData());

            int index = track2String.indexOf("=");
            String pan = track2String.substring(1, index);
            String expirationDate = track2String.substring(index + 1, index + 2);
            if (expirationDate.equals("=")) {
                expirationDate = "";
                index = index + 2;
            } else {
                expirationDate = track2String.substring(index + 1, index + 5);
                index = index + 5;
            }

            String serviceCode = track2String.substring(index, index + 1);
            if (serviceCode.equals("=")) {
                serviceCode = "";
            } else {
                serviceCode = track2String.substring(index, index + 3);
            }

            track2Data.setPAN(pan);
            track2Data.setExpirationDate(expirationDate);
            track2Data.setServiceCode(new ServiceCode(serviceCode));
            track2Data.setRaw(tlvTrack2Data.getRawData());
        }

        return track2Data;

    }

    private static CardStatus parseCardStatus(byte insertStatus, byte swipeStatus) {
        CardStatus cardStatus = new CardStatus();
        cardStatus.setCardPresent((insertStatus & (1 << 0)) > 0);
        cardStatus.setEMVCompatible((insertStatus & (1 << 1)) > 0);
        cardStatus.setMSRDataAvailable((swipeStatus & (1 << 0)) > 0);
        cardStatus.setTrack1DataAvailable((swipeStatus & (1 << 1)) > 0);
        cardStatus.setTrack2DataAvailable((swipeStatus & (1 << 2)) > 0);
        cardStatus.setTrack3DataAvailable((swipeStatus & (1 << 3)) > 0);
        return cardStatus;
    }
}

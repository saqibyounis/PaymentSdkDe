package com.miurasystems.examples.transactions;


import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.miurasystems.miuralibrary.tlv.Track2Data;

import java.io.Serializable;

@SuppressWarnings("serial")
public class MagSwipeSummary implements Serializable {

    @NonNull
    public final Track2Data mMaskedTrack2Data;

    @NonNull
    public final String mSredData;

    @NonNull
    public final String mSredKSN;

    public final boolean mIsPinRequired;

    @Nullable
    public final String mPlainTrack1Data;

    @Nullable
    public final Track2Data mPlainTrack2Data;

    public MagSwipeSummary(
            @NonNull Track2Data maskedTrack2Data,
            @NonNull String sredData,
            @NonNull String sredKSN,
            boolean isPinRequired,
            @Nullable String plainTrack1Data,
            @Nullable Track2Data plainTrack2Data
    ) {
        mMaskedTrack2Data = maskedTrack2Data;
        mSredData = sredData;
        mSredKSN = sredKSN;
        mIsPinRequired = isPinRequired;
        mPlainTrack1Data = plainTrack1Data;
        mPlainTrack2Data = plainTrack2Data;
    }

}

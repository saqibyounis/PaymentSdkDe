package com.miurasystems.examples.rki;


import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;

class HostRkiCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(HostRkiCommand.class);

    private final String mProductionSignCert;

    private final String mTerminalCert;

    private final String mTemporaryCert;

    private final String mSuggestedIKSN;

    public HostRkiCommand(
        String productionSignCert,
        String terminalCert,
        String temporaryCert,
        String suggestedIKSN
    ) {
        mProductionSignCert = productionSignCert;
        mTerminalCert = terminalCert;
        mTemporaryCert = temporaryCert;
        mSuggestedIKSN = suggestedIKSN;
    }

    JSONObject toJson() {
        final HashMap<String, String> rkiDictionary = new HashMap<>();
        rkiDictionary.put("prodSignCert", mProductionSignCert);
        rkiDictionary.put("terminalCert", mTerminalCert);
        rkiDictionary.put("tempLoadCert", mTemporaryCert);
        rkiDictionary.put("suggestedIKSN", mSuggestedIKSN);
        rkiDictionary.put("keyHostIndex", "1");

        return new JSONObject(rkiDictionary);
    }

    @Override
    public String toString() {
        return String.format(
            "HostRkiCommand {"
                + "mProductionSignCert='%s', mTerminalCert='%s', "
                + "mTemporaryCert='%s', mSuggestedIKSN='%s'"
                + "}",
            mProductionSignCert, mTerminalCert, mTemporaryCert, mSuggestedIKSN);
    }
}

package com.miurasystems.examples.rki;


import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class HostRkiResponse {

    private static final Logger LOGGER = LoggerFactory.getLogger(HostRkiResponse.class);

    public final String HsmCert;

    public final String Kbpk;

    public final String KbpkSig;

    public final String SredTr31;

    public final String SredIksn;

    public final String PinTr31;

    public final String PinIksn;

    private HostRkiResponse(
        String hsmCert,
        String kbpk,
        String kbpkSig,
        String sredTr31,
        String sredIksn,
        String pinTr31,
        String pinIksn
    ) {
        HsmCert = hsmCert;
        Kbpk = kbpk;
        KbpkSig = kbpkSig;
        SredTr31 = sredTr31;
        SredIksn = sredIksn;
        PinTr31 = pinTr31;
        PinIksn = pinIksn;
    }

    public static HostRkiResponse valueOf(JSONObject serviceResponse)
        throws TestHostException {

        LOGGER.trace("HostRkiResponse.valueOf: {}", serviceResponse);

        final String result;
        try {
            result = serviceResponse.getString("result");
        } catch (JSONException e) {
            throw new TestHostException("'result' missing from JSON", e);
        }

        if (!result.equals("Success")) {
            String format = String.format("Result is not 'success' is: %s", result);
            throw new TestHostException(format);
        }

        try {
            return new HostRkiResponse(
                serviceResponse.getString("hsmCert"),
                serviceResponse.getString("kbpk"),
                serviceResponse.getString("kbpkSig"),
                serviceResponse.getString("sredTr31"),
                serviceResponse.getString("sredIksn"),
                serviceResponse.getString("pinTr31"),
                serviceResponse.getString("pinIksn")
            );
        } catch (JSONException e) {
            throw new TestHostException("Invalid JSON", e);
        }
    }
}

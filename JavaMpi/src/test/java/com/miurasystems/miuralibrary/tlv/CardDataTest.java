/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.tlv;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.text.IsEqualIgnoringCase.equalToIgnoringCase;
import static org.junit.Assert.assertThat;

import com.miurasystems.miuralibrary.JavaBeanTester;
import com.miurasystems.miuralibrary.enums.ServiceCode;

import org.junit.Assert;
import org.junit.Test;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.List;

public class CardDataTest {
    @Test
    public void toStringNew() throws Exception {
        // ensure a fresh object's toString works without exploding.
        String s = new CardData().toString();
        assertThat(s, not(isEmptyOrNullString()));
    }

    @Test
    public void testGetSets() throws Exception {
        JavaBeanTester.test(CardData.class);
    }

    @Test
    public void valueOf_secured() {

        /*
            ped with keys:
            E1 [Response Data], 81B7 (183)
                48 [Card Status], 02
                    0007
                    |..|
                DFAE02 [SRED Data], 78
                    72212907C9B93D919511254CA428CE061009857EC1F129D3DE63CBF9F7D5452C36C961C881A11E84
                        EF2F7D6C223C12CF2CC55A87AFC2D097F57D07BFF787A997CA5DB3E0FE46AF9C7C37580459EE
                        6F6065CAD8E1BDACAE01B8ED4E06D232485F3B7A72C88119E3DF27F3553284411FCC8DC25EFC
                        8ECF4AFA
                    |r!)...=...%L.(.....~..)..c....E,6.a....../}l"<..,.Z......}.......]...F..|7X.Y.o
                        `e.........N..2H_;zr.....'.U2.A....^...J.|
                DFAE03 [SRED KSN], 0A
                    00000200000055400182
                    |......U@..|
                DFAE22 [Masked Track 2], 25
                    3B3437363137332A2A2A2A2A2A303031303D313531323230312A2A2A2A2A2A2A2A2A2A3F2A
                    |;476173******0010=1512201**********?*|
         */

        // Setup
        TLVObject cardStatusTlv = new TLVObject(Description.Card_Status, decodeHex("0007"));
        TLVObject sredDataTlv = new TLVObject(
                Description.SRED_Data,
                decodeHex("72212907C9B93D919511254CA428CE061009857EC1F129D3DE63CBF9F7D5452C"
                        + "36C961C881A11E84EF2F7D6C223C12CF2CC55A87AFC2D097F57D07BFF787A997"
                        + "CA5DB3E0FE46AF9C7C37580459EE6F6065CAD8E1BDACAE01B8ED4E06D232485F"
                        + "3B7A72C88119E3DF27F3553284411FCC8DC25EFC8ECF4AFA"
                )
        );
        TLVObject sredKsnTlv = new TLVObject(
                Description.SRED_KSN,
                decodeHex("00000200000055400182")
        );
        TLVObject maskedTrack2Tlv = new TLVObject(
                Description.Masked_Track_2,
                decodeHex("3B3437363137332A2A2A2A2A2A303031303D313531323230312A2A2A2A2A2A2A"
                        + "2A2A2A3F2A")
        );

        List<TLVObject> responseDataTlvList = Arrays.asList(
                cardStatusTlv, sredDataTlv, sredKsnTlv, maskedTrack2Tlv
        );
        TLVObject responseDataTlv = new TLVObject(Description.Response_Data, responseDataTlvList);

        // Test
        CardData cardData = CardData.valueOf(responseDataTlv);

        // Verify
        assertThat(cardData.getAnswerToReset(), is(nullValue()));

        CardStatus cardStatus = cardData.getCardStatus();
        assertThat(cardStatus.isCardPresent(), is(false));
        assertThat(cardStatus.isEMVCompatible(), is(false));
        assertThat(cardStatus.isMSRDataAvailable(), is(true));
        assertThat(cardStatus.isTrack1DataAvailable(), is(true));
        assertThat(cardStatus.isTrack2DataAvailable(), is(true));
        assertThat(cardStatus.isTrack3DataAvailable(), is(false));

        assertThat(cardData.getSredData(), is(equalToIgnoringCase(
                "72212907C9B93D919511254CA428CE061009857EC1F129D3DE63CBF9F7D5452C"
                        + "36C961C881A11E84EF2F7D6C223C12CF2CC55A87AFC2D097F57D07BFF787A997"
                        + "CA5DB3E0FE46AF9C7C37580459EE6F6065CAD8E1BDACAE01B8ED4E06D232485F"
                        + "3B7A72C88119E3DF27F3553284411FCC8DC25EFC8ECF4AFA"

        )));
        assertThat(cardData.getSredKSN(), is(equalToIgnoringCase("00000200000055400182")));

        // real:    4761739001010010=15122011143857589
        assertThat(cardData.getPlainTrack1Data(), is(nullValue()));
        assertThat(cardData.getPlainTrack2Data(), is(nullValue()));

        // masked: ;476173******0010=1512201**********?*
        Track2Data maskedTrack2Data = cardData.getMaskedTrack2Data();
        assertThat(maskedTrack2Data.isMasked(), is(equalTo(true)));
        assertThat(maskedTrack2Data.getPAN(), is(equalTo("476173******0010")));
        assertThat(maskedTrack2Data.getExpirationDate(), is(equalTo("1512")));

        // manually check equal to
        ServiceCode serviceCode = maskedTrack2Data.getServiceCode();
        ServiceCode expectedServiceCode = new ServiceCode("201");
        assertThat(serviceCode.getFirstDigit(), is(equalTo(expectedServiceCode.getFirstDigit())));
        assertThat(serviceCode.getSecondDigit(), is(equalTo(expectedServiceCode.getSecondDigit())));
        assertThat(serviceCode.getThirdDigit(), is(equalTo(expectedServiceCode.getThirdDigit())));

        assertThat(cardData.toString(), not(containsString("4761739001010010")));
    }

    @Test
    public void valueOf_unsecured() throws UnsupportedEncodingException {
        /*
            ped without keys:
            E1 [Response Data] 819E (158)
                48 [Card Status]  02  ( 2)
                    0007
                    |..|
                5F21 [MAG Track 1]    46 (70)
                    2542343736313733393030313031303031305E564953412041435155495245522054455354204341
                        52442030355E31353132323031313134333830303537353030303030303F
                    |%B4761739001010010^VISA ACQUIRER TEST CARD 05^15122011143800575000000?|
                5F22 [MAG Track 2]    25 (37)
                    3B343736313733393030313031303031303D31353132323031313134333835373538393F3C
                    |;4761739001010010=15122011143857589?<|
                DFAE22 [Masked Track 2] 25 (37)
                    3B3437363137332A2A2A2A2A2A303031303D313531323230312A2A2A2A2A2A2A2A2A2A3F2A
                    |;476173******0010=1512201**********?*|

         */

        // Setup
        TLVObject cardStatusTlv = new TLVObject(Description.Card_Status, decodeHex("0007"));

        TLVObject magTrack1 = new TLVObject(
                Description.Track_1,
                "%B4761739001010010^VISA ACQUIRER TEST CARD 05^15122011143800575000000?".getBytes(
                        "US-ASCII"
                )
        );
        TLVObject magTrack2 = new TLVObject(
                Description.Track_2,
                ";4761739001010010=15122011143857589?<".getBytes("US-ASCII")
        );
        TLVObject maskedTrack2Tlv = new TLVObject(
                Description.Masked_Track_2,
                ";476173******0010=1512201**********?*".getBytes("US-ASCII")
        );

        List<TLVObject> responseDataTlvList = Arrays.asList(
                cardStatusTlv, magTrack1, magTrack2, maskedTrack2Tlv
        );
        TLVObject responseDataTlv = new TLVObject(Description.Response_Data, responseDataTlvList);

        // Test
        CardData cardData = CardData.valueOf(responseDataTlv);

        // Verify
        assertThat(cardData.getAnswerToReset(), is(nullValue()));

        CardStatus cardStatus = cardData.getCardStatus();
        assertThat(cardStatus.isCardPresent(), is(false));
        assertThat(cardStatus.isEMVCompatible(), is(false));
        assertThat(cardStatus.isMSRDataAvailable(), is(true));
        assertThat(cardStatus.isTrack1DataAvailable(), is(true));
        assertThat(cardStatus.isTrack2DataAvailable(), is(true));
        assertThat(cardStatus.isTrack3DataAvailable(), is(false));

        assertThat(cardData.getSredData(), is(nullValue()));
        assertThat(cardData.getSredKSN(), is(nullValue()));

        String plainTrack1Data = cardData.getPlainTrack1Data();
        assertThat(plainTrack1Data, is(equalTo(
                "%B4761739001010010^VISA ACQUIRER TEST CARD 05^15122011143800575000000?"
        )));


        // real:    4761739001010010=15122011143857589
        Track2Data plainTrack2Data = cardData.getPlainTrack2Data();
        assertThat(plainTrack2Data.isMasked(), is(equalTo(false)));
        assertThat(plainTrack2Data.getPAN(), is(equalTo("4761739001010010")));
        assertThat(plainTrack2Data.getExpirationDate(), is(equalTo("1512")));

        // masked: ;476173******0010=1512201**********?*
        Track2Data maskedTrack2Data = cardData.getMaskedTrack2Data();
        assertThat(maskedTrack2Data.isMasked(), is(equalTo(true)));
        assertThat(maskedTrack2Data.getPAN(), is(equalTo("476173******0010")));
        assertThat(maskedTrack2Data.getExpirationDate(), is(equalTo("1512")));

        // manually check equal to
        ServiceCode serviceCode = maskedTrack2Data.getServiceCode();
        ServiceCode expectedServiceCode = new ServiceCode("201");
        assertThat(serviceCode.getFirstDigit(), is(equalTo(expectedServiceCode.getFirstDigit())));
        assertThat(serviceCode.getSecondDigit(), is(equalTo(expectedServiceCode.getSecondDigit())));
        assertThat(serviceCode.getThirdDigit(), is(equalTo(expectedServiceCode.getThirdDigit())));

        assertThat(cardData.toString(), not(containsString("4761739001010010")));

    }

    private static byte[] decodeHex(String string) {
        return BinaryUtil.parseHexBinary(string);
    }
}

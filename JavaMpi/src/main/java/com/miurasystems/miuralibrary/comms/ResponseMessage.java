/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.comms;


import static com.miurasystems.miuralibrary.tlv.BinaryUtil.ubyteToInt;

import android.support.annotation.NonNull;
import android.support.annotation.Size;

import com.miurasystems.miuralibrary.enums.InterfaceType;

import java.util.Arrays;

/**
 * Represents a Response Application Protocol Data Unit (APDU) from a Miura Device, based on
 * the MPI API doc and also vaguely based on ISO-7816-3. This response APDU is the concatenation of
 * the "information fields" in a {@link MpiPacket response packet(s)}.
 *
 * A response may be 'solicited' or not. A solicited response is sent in response to a command.
 * An unsolicited response is sent whenever the device likes.
 *
 * A response contains any information sent from the Miura device as the 'body', and also has
 * a status code that says whether a command succeed or not. (In the case of an unsolicited
 * response, the status code means whatever the MPI API defines it to mean)
 *
 * The contents and format of the body are entirely dependent on the command that is being
 * responded to and are therefore treated as bytes by ResponseMessage. It's up to the code reading
 * the response to interpret those bytes correctly.
 *
 * Responses are sent from the Miura device in a packetised form (see {@link MpiPacket}) and
 * a single response can be split over multiple packets. Therefore responses should be
 * read from a bytestream by a {@link ResponseReader} rather than being constructed directly.
 */
public class ResponseMessage {

    /**
     * The data that makes up the 'body' of the packet.
     */
    @NonNull
    private final byte[] mBytes;

    /**
     * The Miura device this ResponseMessage came from.
     */
    @NonNull
    private final InterfaceType mAddress;

    /**
     * If the response is unsolicited or not.
     * true = unsolicited
     * false = solicited
     */
    private final boolean mUnsolicited;

    /**
     * Construct a Response Message
     *
     * @param address     The node address of the device that sent this response
     * @param unsolicited true if the response was unsolicited, false if it was solicited
     * @param bytes       the entire response APDU after being extracted from the MPI packets.
     *                    It should always include the SW12 status bytes.
     */
    public ResponseMessage(
            @NonNull InterfaceType address,
            boolean unsolicited,
            @NonNull @Size(min = 2) byte[] bytes) {
        // must be at least 2 bytes: sw12
        if (bytes.length < 2) {
            throw new IllegalArgumentException("bytes too small. Minimum: 2");
        }

        mAddress = address;
        mUnsolicited = unsolicited;
        mBytes = bytes;
    }

    /**
     * Was the Response a success?
     *
     * Success is defined in MPI doc and ISO-7816-3 to mean
     * "a status code of 0x9000"
     *
     * @return true if response represents a success response, false otherwise
     */
    public final boolean isSuccess() {
        return this.getStatusCode() == 0x9000;
    }

    /**
     * Get the status code as an int.
     *
     * Even though it's 2 bytes, it's an int rather than a short,
     * due to common status codes, e.g. 0x9000, being a pain in java due to
     * them being negative.
     *
     * Regarding endianness:
     * if SW1=AA, SW2=55 then getStatusCode() returns 0xAA55.
     *
     * @return SW1|SW2 as an int status code
     */
    public final int getStatusCode() {
        final int sw1 = ubyteToInt(getSw1());
        final int sw2 = ubyteToInt(getSw2());
        return (sw1 << 8 | sw2) & 0xFFFF;
    }

    /**
     * Get the 'body' of the ResponseMessage.
     *
     * Where 'body' means "all of the useful bytes from the information field that aren't
     * the status code".
     *
     * Note that a body might be empty i.e., that a Response was simply nothing but
     * a status code.
     *
     * @return A <b>copy</b> of the body.
     */
    @NonNull
    public final byte[] getBody() {
        return Arrays.copyOf(mBytes, mBytes.length - 2);
    }

    /**
     * Is the Response and unsolicited response?
     *
     * @return true if the response is unsolicited, false if the response is solicited
     */
    public final boolean isUnsolicited() {
        return mUnsolicited;
    }

    /**
     * Get the node address the Response was sent from
     *
     * @return the node address
     */
    @NonNull
    public final InterfaceType getNodeAddress() {
        return mAddress;
    }

    /**
     * Get the first byte of the status code, SW1
     *
     * @return SW1
     */
    public final byte getSw1() {
        return mBytes[mBytes.length - 2];
    }

    /**
     * Get the second byte of the status code, SW2
     *
     * @return SW2
     */
    public final byte getSw2() {
        return mBytes[mBytes.length - 1];
    }

    @Override
    public String toString() {
        return "ResponseMessage{" +
                "mBytes=" + Arrays.toString(mBytes) +
                ", mAddress=" + mAddress +
                ", mUnsolicited=" + mUnsolicited +
                '}';
    }
}


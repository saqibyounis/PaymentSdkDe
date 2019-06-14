/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.comms;

import static com.miurasystems.miuralibrary.tlv.BinaryUtil.assertByteRange;
import static com.miurasystems.miuralibrary.tlv.BinaryUtil.intToUbyte;
import static com.miurasystems.miuralibrary.tlv.BinaryUtil.ubyteToInt;

import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.Size;

import com.miurasystems.miuralibrary.enums.InterfaceType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;


/**
 * Represents an MPI packet/block frame, which are based on ISO-7816-3 T1=1.
 *
 * The class is intended to deal with de/serialising the packets over byte
 * streams and in reconstructing chained packets into their original message content.
 *
 * MpiPacket objects are immutable value objects. Once constructed they stay as they are.
 *
 * The packets are a sequence of bytes:
 * <pre>
 *  Prologue:
 *      NAD, PCB, LEN,
 *  Information field:
 *      [LEN number of bytes, minimum of 2]
 *  Epilogue:
 *      LRC
 * </pre>
 * intended to carry a message, aka an Application Protocol Data Unit (APDU),
 * over a communications link.
 *
 * MpiPacket doesn't care about the contents of the APDU, only that it's the correct size:
 * see {@link #MIN_APDU_SIZE} and {@link #MAX_APDU_SIZE}.
 *
 * See section 5 'Packets' in MPI API doc for more information.
 */
public final class MpiPacket {
    //region constants
    // region packet-sizes

    /** Minimum APDU message size. (i.e. the minimum LEN value) */
    static final int MIN_APDU_SIZE = 2;

    /** Maximum APDU message size. (i.e. the maximum LEN value) */
    static final int MAX_APDU_SIZE = 254;

    /** SLF4J Logger */
    private static final Logger LOGGER = LoggerFactory.getLogger(MpiPacket.class);

    /** Size of the prologue/header, in bytes */
    private static final int HEADER_SIZE = 3;

    /** Size of the epilogue/footer, in bytes */
    private static final int FOOTER_SIZE = 1;

    /** Total size of the constant 'overhead' for each message. */
    private static final int OVERHEAD_SIZE = HEADER_SIZE + FOOTER_SIZE;

    /** Minimum packet size. i.e. minimum size {@link #getBytes()} will return. */
    private static final int MIN_PACKET_SIZE = MIN_APDU_SIZE + OVERHEAD_SIZE;

    /** Maximum packet size. i.e. maximum size {@link #getBytes()} will return. */
    private static final int MAX_PACKET_SIZE = MAX_APDU_SIZE + OVERHEAD_SIZE;
    //endregion

    // region field-offsets
    /** Byte-offset into a packet for the 'NAD' field */
    private static final int PKT_NAD = 0;

    /** Byte-offset into a packet for the 'PCB' field */
    private static final int PKT_PCB = 1;

    /** Byte-offset into a packet for the 'LEN' field */
    private static final int PKT_LEN = 2;

    /** Byte-offset into a packet for the start of the 'APDU' field */
    private static final int PKT_APDU = 3;
    // endregion

    /**
     * The 'chained' bit in the PCB byte.
     *
     * <p>When set it indicates that there are more packets to receive and that multiple packets
     * make up the APDU.
     * {@link #isChained()} {@link #reconstructApdu(List)}
     */
    private static final int PCB_CHAINED = 0x1;

    /** The 'unsolicited' bit in the PCB byte. {@link #isUnsolicited()} */
    private static final int PCB_UNSOLICITED = 0x40;
    //endregion

    /**
     * The actual bytes that make up the packet and that will be sent/read to a byte stream.
     */
    @NonNull
    private final byte[] mPacket;

    /**
     * Create a packet for the given InterfaceType ('NAD') and APDU.
     *
     * This is intended for Commands APDUs and therefore
     * solicited/unsolicited is ignored.
     *
     * @param ifType    InterfaceType/Node Address/NAD
     *                  Must be a valid InterfaceType reference.
     * @param apduBytes The APDU's bytes.
     *                  Must be a valid byte array with size in the range:
     *                  [MIN_APDU_SIZE, MAX_APDU_SIZE]
     */
    public MpiPacket(
            @NonNull InterfaceType ifType,
            @NonNull @Size(min = MIN_APDU_SIZE, max = MAX_APDU_SIZE) byte[] apduBytes
    ) {
        this(ifType.getInterfaceType(), 0x0, apduBytes);
    }

    /**
     * Create a packet given the NAD and PCB bytes and the APDU data.
     *
     * @param nad       NAD byte
     *                  The node address of the system that will process the packet.
     *                  {@link InterfaceType}
     * @param pcb       PCB byte
     *                  Command packets should be 0x0
     *                  Response packets can set bits {@link #PCB_CHAINED} and {@link
     *                  #PCB_UNSOLICITED}
     * @param apduBytes The APDU's bytes.
     *                  Must be a valid byte array with size in the range:
     *                  [MIN_APDU_SIZE, MAX_APDU_SIZE]
     */
    MpiPacket(
            @IntRange(from = 0, to = 255) int nad,
            @IntRange(from = 0, to = 255) int pcb,
            @NonNull @Size(min = MIN_APDU_SIZE, max = MAX_APDU_SIZE) byte[] apduBytes
    ) {
        if (apduBytes.length < MIN_APDU_SIZE || apduBytes.length > MAX_APDU_SIZE) {
            throw new IllegalArgumentException(String.format(Locale.ENGLISH,
                    "Invalid apduBytes length: %s. min: %s max: %s",
                    apduBytes.length, MIN_APDU_SIZE, MAX_APDU_SIZE));
        }

        final byte[] packet = new byte[OVERHEAD_SIZE + apduBytes.length];
        packet[PKT_NAD] = intToUbyte(nad);
        packet[PKT_PCB] = intToUbyte(pcb);
        packet[PKT_LEN] = intToUbyte(apduBytes.length);

        System.arraycopy(apduBytes, 0, packet, PKT_APDU, apduBytes.length);

        final byte lrc = calculateLRC(packet, 0, packet.length - 1);
        packet[packet.length - 1] = lrc;

        validatePacket(packet);
        mPacket = packet;
    }

    /**
     * Create a packet from the given bytes
     *
     * @param packet The entire packet's bytes.
     *               Must be a valid byte array with size in the range:
     *               [MIN_PACKET_SIZE, MAX_PACKET_SIZE]
     */
    MpiPacket(@NonNull @Size(min = MIN_PACKET_SIZE, max = MAX_PACKET_SIZE) byte[] packet) {
        validatePacket(packet);
        mPacket = packet;
    }

    /**
     * Get the bytes that make up this packet.
     *
     * <p>Used if you want to stream the packet, e.g.:
     *
     * {@code outputStream.write(packet.getBytes()); }
     *
     * @return a copy of the byte array.
     */
    @NonNull
    byte[] getBytes() {
        return Arrays.copyOf(mPacket, mPacket.length);
    }

    /**
     * Tells if the packet is a 'chained' packet
     *
     * @return true if chained, false otherwise
     */
    public boolean isChained() {
        return (mPacket[PKT_PCB] & PCB_CHAINED) == PCB_CHAINED;
    }

    /**
     * Tells if the packet is an 'unsolicited' packet
     *
     * @return true if an unsolicited packet, false if a solicited packet.
     */
    public boolean isUnsolicited() {
        return (mPacket[PKT_PCB] & PCB_UNSOLICITED) == PCB_UNSOLICITED;
    }

    /**
     * Get the node address that this packet should/has been dealt by
     *
     * <p>If this packet was read from the PED, nodeAddress says who sent it.
     * If this packet is to be sent to the PED, nodeAddress says who will deal with it.
     *
     * @return a valid InterfaceType
     */
    @NonNull
    public InterfaceType getNodeAddress() {
        // NAD stands for Node Address
        final byte nad = mPacket[PKT_NAD];

        // This cannot be a null return value, as the constructors verify that
        // the NAD byte is a legitimate enum value and vice-versa.
        final InterfaceType e = InterfaceType.valueOf(nad);
        if (e == null) throw new AssertionError();
        return e;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final MpiPacket mpiPacket = (MpiPacket) o;

        return Arrays.equals(mPacket, mpiPacket.mPacket);

    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(mPacket);
    }

    /**
     * Ensure that the byte array is a valid 'packet'.
     *
     * <p>Checks NAD/PCB/LEB/APDU size/Packet size against MPI API spec.
     * Also validates the LRC is correct.
     *
     * <p>Raises an IllegalArgumentException if the packet is invalid.
     *
     * @param packet The packet's bytes
     */
    private static void validatePacket(@NonNull byte[] packet) {
        if (packet.length < MIN_PACKET_SIZE || packet.length > MAX_PACKET_SIZE) {
            throw new IllegalArgumentException(String.format(Locale.ENGLISH,
                    "Invalid packet byte[] length: %s. min: %s max: %s",
                    packet.length, MIN_PACKET_SIZE, MAX_PACKET_SIZE));
        }
        if (!validateNad(ubyteToInt(packet[PKT_NAD]))) {
            throw new IllegalArgumentException(String.format(Locale.ENGLISH,
                    "Invalid NAD 0x%02x", packet[PKT_NAD]));
        }
        if (!validatePcb(ubyteToInt(packet[PKT_PCB]))) {
            throw new IllegalArgumentException(String.format(Locale.ENGLISH,
                    "Invalid PCB 0x%02x", packet[PKT_PCB]));
        }
        final int lenByte = ubyteToInt(packet[PKT_LEN]);
        if (!validateLen(lenByte)) {
            throw new IllegalArgumentException(String.format(Locale.ENGLISH,
                    "Invalid LEN/APDU length 0x%02x", packet[PKT_LEN]));
        }
        if (lenByte + OVERHEAD_SIZE != packet.length) {
            final String fmt = String.format(Locale.ENGLISH,
                    "Byte array length (0x%02x) doesn't match LEN=0x%02x + %s?",
                    packet.length, lenByte, OVERHEAD_SIZE);
            throw new IllegalArgumentException(fmt);
        }

        final byte expectedLrc = calculateLRC(packet, 0, packet.length - 1);
        final byte theirLrc = packet[packet.length - 1];
        if (expectedLrc != theirLrc) {
            throw new IllegalArgumentException(String.format(Locale.ENGLISH,
                    "Invalid LRC: 0x%02x. Expected: 0x%02x", theirLrc, expectedLrc));
        }
    }

    /**
     * Calculate the LRC of the given bytes.
     *
     * <p>LRC = Longitudinal Redundancy Check, aka the checksum of the packet.
     *
     * <p>Note that the LRC byte should xor with the LRC of the entire packet to be 0,
     * i.e. a valid packet should have the property:
     *
     * <pre>{@code
     * byte[] bytes = packet.getBytes();
     * assert packet.calculateLRC(bytes, 0, bytes.length) == 0;
     * }
     * </pre>
     *
     * @param packet The bytes to calculate the LRC of
     * @param start  The offset into {@code packet} to start reading
     * @param length The number of bytes of {@code packet} to read, starting at start.
     * @return the LRC
     */
    private static byte calculateLRC(
            @NonNull byte[] packet,
            @IntRange(from = 0, to = MAX_PACKET_SIZE - 1) int start,
            @IntRange(from = 1, to = MAX_PACKET_SIZE) int length
    ) {
        // xor in java only works on ints, so calculate it all as ints and return a byte
        int lrc = 0;
        for (int i = start; i < length; i++) {
            lrc = ((int) packet[i]) ^ lrc;
        }
        return (byte) lrc;
    }

    /**
     * Reads a byte from {@code stream} and returns it if it's valid. Blocks whilst doing so.
     *
     * <p>A byte is read from the input stream. Then validator.validateByte() is called on it.
     * If the validator returns true the byte is returned, otherwise null is returned.
     *
     * <p>In-case of a stream error: null is returned and the error is logged.
     *
     * @param stream    Input stream to read from.
     * @param what      The "name" of the byte being read from the stream. Used in error messages.
     * @param validator Callback to validate the byte.
     *                  Should return true if the byte is valid
     *                  Should return false if the byte is invalid.
     * @return The byte, if it's valid, or null if note.
     */
    @Nullable
    private static Byte readByteAndValidate(
            @NonNull InputStream stream,
            @NonNull String what,
            @NonNull Validator validator
    ) {
        final int i;
        try {
            i = stream.read();
        } catch (IOException ex) {
            LOGGER.debug("Failed to read {} byte from stream: {}", what, ex.toString());
            return null;
        }
        if (i == -1) {
            LOGGER.debug("Failed to read {} byte from stream. EOF", what);
            return null;
        }

        final byte b = intToUbyte(i);
        if (validator.validateByte(i)) {
            return b;
        } else {
            LOGGER.info("Bad {} byte! ({}) failed validation", what, String.format("0x%02x", b));
            return null;
        }
    }

    /**
     * Tells if the given 'byte' is a valid NAD byte for a packet
     *
     * @param nad A byte in the range [0, 255]
     * @return true if the NAD looks valid, false if it looks invalid
     */
    private static boolean validateNad(@IntRange(from = 0, to = 255) int nad) {
        final byte bNad = intToUbyte(nad);
        return InterfaceType.valueOf(bNad) != null;
    }

    /**
     * Tells if the given 'byte' is a valid PCB byte for a packet
     *
     * @param pcb A byte in the range [0, 255]
     * @return true if the PCB looks valid, false if it looks invalid
     */
    private static boolean validatePcb(@IntRange(from = 0, to = 255) int pcb) {
        assertByteRange(pcb);
        // can't differentiate command vs response at this level, so
        // no need to care about the fact that unsolicited won't be chained
        final int validMask = PCB_CHAINED | PCB_UNSOLICITED;
        return (pcb & validMask) == pcb;
    }

    /**
     * Tells if the given 'byte' is a valid LEN byte for a packet
     *
     * <p>Valid LEN should be [MIN_APDU_SIZE, MAX_APDU_SIZE]
     *
     * @param len A 'byte' in the range [0, 255]
     * @return true if the LEN looks valid, false if it looks invalid
     */
    private static boolean validateLen(@IntRange(from = 0, to = 255) int len) {
        assertByteRange(len);
        if (len > MAX_APDU_SIZE) {
            return false;
        } else if (len < MIN_APDU_SIZE) {
            return false;
        }
        return true;
    }

    /**
     * Reads a packet header from a stream. Blocks whilst doing so.
     *
     * <p>Reads as little as possible. If any of the bytes look invalid then the reading is aborted
     * and null is returned.
     *
     * @param stream The stream to read the bytes from
     * @return The header (byte[3]) if it was read from the stream ok.
     * If there was a stream error or the bytes didn't look like valid header bytes,
     * null is returned.
     */
    @Size(value = 3)
    @Nullable
    private static byte[] readPacketHeader(@NonNull InputStream stream) {
        /*
            Java 8 this would be:
            Byte nad = readByteAndValidate(stream, MpiPacket::validateNad);
            if (nad == null) return null;
            ...
        */
        final Byte nad = readByteAndValidate(stream, "NAD", new Validator() {
            @Override
            public boolean validateByte(int b) {
                return validateNad(b);
            }
        });
        if (nad == null) return null;

        final Byte pcb = readByteAndValidate(stream, "PCB", new Validator() {
            @Override
            public boolean validateByte(int b) {
                return validatePcb(b);
            }
        });
        if (pcb == null) return null;

        final Byte len = readByteAndValidate(stream, "LEN", new Validator() {
            @Override
            public boolean validateByte(int b) {
                return validateLen(b);
            }
        });
        if (len == null) return null;

        final byte[] output = new byte[HEADER_SIZE];
        output[PKT_NAD] = nad;
        output[PKT_PCB] = pcb;
        output[PKT_LEN] = len;
        return output;
    }

    /**
     * Reads a packet header from a stream, and on error calls {@link InputStream#reset()}.
     *
     * Will block whilst waiting for the stream.
     *
     * <p>Reads as little as possible. If any of the bytes look invalid then the reading is aborted,
     * null is returned, and the error is logged.
     *
     * <p>If the stream supports 'marking' via {@link InputStream#markSupported()} then,
     * in case of an error, {@link InputStream#mark(int)} and {@link InputStream#reset()} are used
     * in an attempt to "roll-back" the stream.
     *
     * @param stream The stream to read the bytes from
     * @return The header (byte[3]) if it was read from the stream ok.
     * If there was a stream error or the bytes didn't look like valid header bytes,
     * null is returned.
     */
    @Size(value = 3)
    @Nullable
    private static byte[] readPacketHeaderTransaction(@NonNull InputStream stream) {

        if (stream.markSupported()) {
            stream.mark(HEADER_SIZE);
        }
        final byte[] output = readPacketHeader(stream);
        if (output != null) {
            return output;
        }

        // There's been an error or an invalid byte,
        // so roll back the stream if it supports it.
        // If it doesn't support it or rollback fails,
        // there's not much we can do.
        if (stream.markSupported()) {
            try {
                stream.reset();
            } catch (IOException ex) {
                LOGGER.info("Failed to reset stream after readPacketHeader error!", ex);
            }
        }
        return null;
    }

    /**
     * Reads a single packet from the stream. Blocks whilst doing so.
     *
     * <p>Reads as little as possible. Reads from the stream only the number of bytes required to
     * complete the packet.
     *
     * <p>If any of the header bytes look invalid then the reading is
     * aborted, null is returned.
     *
     * <p>In the case of a stream error or the steam closing to early, null is returned.
     *
     * <p>If the packet fails the LRC check, null is returned.
     *
     * <p>If the stream supports 'marking' via {@link InputStream#markSupported()} then,
     * in case of an error or invalid byte whilst reading the header,
     * {@link InputStream#mark(int)} and {@link InputStream#reset()} are used in an attempt to
     * "roll-back" the stream.
     *
     * <p>In all cases where null is returned, the appropriate error is logged.
     *
     * @param stream The input stream to read from.
     * @return A valid MPI packet. Or, if an error occurred: null.
     */
    @Nullable
    public static MpiPacket readFromStream(@NonNull InputStream stream) {
        final byte[] header = readPacketHeaderTransaction(stream);
        if (header == null) {
            return null;
        }

        final int apduLength = ubyteToInt(header[PKT_LEN]);
        final byte[] packet = new byte[OVERHEAD_SIZE + apduLength];
        System.arraycopy(header, 0, packet, 0, HEADER_SIZE);

        // read (LEN bytes + LRC) from the stream
        final int totalLength = apduLength + FOOTER_SIZE;
        int totalNumRead = 0;
        while (totalNumRead < totalLength) {

            final int remainingLength = totalLength - totalNumRead;
            final int numRead;
            try {
                final int offset = PKT_APDU + totalNumRead;
                numRead = stream.read(packet, offset, remainingLength);
            } catch (IOException ex) {
                LOGGER.debug("Failed to read stream. totalNumRead:{}, remainingLength:{}!",
                        totalNumRead, remainingLength, ex);
                return null;
            }
            if (numRead <= 0) {
                LOGGER.debug("EOF. stream.read() returned: {}", numRead);
                if (numRead == 0) {
                    // According to the docs, 0 shouldn't be returned unless
                    // we pass in a 0 length buffer, and we don't.
                    // Only -1 or > 0 should be valid. But just in case...
                    LOGGER.warn("0 returned from read?");
                }
                return null;
            }
            totalNumRead += numRead;
        }

        final byte expectedLrc = calculateLRC(packet, 0, packet.length - 1);
        final byte theirLrc = packet[packet.length - 1];
        if (expectedLrc != theirLrc) {
            LOGGER.info("Bad LRC: {}. Expected:{}",
                    String.format("0x%02x", theirLrc),
                    String.format("0x%02x", expectedLrc));
            return null;
        }

        return new MpiPacket(packet);
    }

    /**
     * Extract the APDU from packets.
     *
     * <p>If packets.size() == 1, then this is equivalent to extracting the APDU from the packet
     * and it is assumed that:
     * <ol> <li>packets[0] must have `{@link #isChained()} == false`. </li> </ol>
     *
     * <p>If packets.size() > 1, then it is assumed that:
     * <ol>
     * <li>{@code packets} is a list of chained
     * packets and the APDU is spread across them all.</li>
     * <li>All of the packets in the list
     * must have `{@link #isChained()} == true`, except the last "terminating" packet which has
     * `{@link #isChained()} == false`.
     * </li>
     * <li> All of the packets in the list have the same solicited/unsolicited status. </li>
     * <li> All of the packets in the list have the same node address. </li>
     * </ol>
     *
     * If these conditions are violated then IllegalArgumentException is throw.
     *
     * @param packets A list of packets to extract the APDU from
     * @return the extracted APDU
     */
    @Size(min = 2)
    @NonNull
    public static byte[] reconstructApdu(@NonNull @Size(min = 1) List<MpiPacket> packets) {
        if (packets.size() < 1) {
            throw new IllegalArgumentException(String.format(Locale.ENGLISH,
                    "Invalid number of packets: %s", packets.size()));
        }

        /*
            If we get so many packets that we rollover an int, then
            java will simply crash when we try and write all of them,
            which is fine.

            Also, we're dealing with MpiPacket objects here, which will
            have all validated their length to be > MIN_APDU_SIZE, so
            no need to worry about 0-sized APDU etc.
        */
        int totalLength = 0;

        boolean firstIsUnsolicited = packets.get(0).isUnsolicited();
        InterfaceType firstNodeAddress = packets.get(0).getNodeAddress();

        for (int i = 0; i < packets.size(); i++) {
            MpiPacket packet = packets.get(i);

            boolean isLast = (i == (packets.size() - 1));
            boolean unfinishedList = isLast == packet.isChained();
            if (unfinishedList) {
                String s = "packets must contain exactly one unchained packet,"
                        + " and it should be at the end of the list";
                throw new IllegalArgumentException(s);
            }
            if (packet.isUnsolicited() != firstIsUnsolicited) {
                String s = "All packets in the list should have the same solicited status";
                throw new IllegalArgumentException(s);
            }
            if (packet.getNodeAddress() != firstNodeAddress) {
                String s = "All packets in the list should have the same node address";
                throw new IllegalArgumentException(s);
            }

            totalLength += ubyteToInt(packet.mPacket[PKT_LEN]);
        }

        byte[] constructedApdu = new byte[totalLength];
        int writeOffset = 0;
        for (MpiPacket packet : packets) {
            int apduLength = ubyteToInt(packet.mPacket[PKT_LEN]);
            System.arraycopy(
                    packet.mPacket, PKT_APDU,
                    constructedApdu, writeOffset,
                    apduLength);
            writeOffset += apduLength;
        }

        return constructedApdu;
    }

    /**
     * Packetise data and write it to an OutputStream
     *
     * Currently only supports one packet's worth of data (MAX_APDU_SIZE).
     *
     * Even if writeToStream returns false (e.g. due to an error) doesn't mean nothing was sent.
     * The other end of the stream might have seen none, some, or all of the bytes.
     *
     * @param nad       Node Address/Miura Device that the data should be sent to.
     * @param apduBytes The data to send to the Miura Device.
     * @param stream    stream to write packet to
     * @return True if the packet wrote to the stream successfully,
     * false if there was an error writing to the stream.
     */
    public static boolean writeToStream(
            @NonNull InterfaceType nad,
            @NonNull @Size(min = MIN_APDU_SIZE, max = MAX_APDU_SIZE) byte[] apduBytes,
            @NonNull OutputStream stream) {

        MpiPacket packet = new MpiPacket(nad, apduBytes);
        try {
            stream.write(packet.mPacket, 0, packet.mPacket.length);
        } catch (IOException e) {
            LOGGER.debug("Exception when writing to stream", e);
            return false;
        }

        try {
            stream.flush();
        } catch (IOException e) {
            LOGGER.debug("Exception when flushing?", e);
            return false;
        }
        return true;
    }

    /**
     * Callback for use with readByteAndValidate
     */
    interface Validator {

        /**
         * Validate a byte against whatever scheme the implementation of this interface wants to.
         *
         * @param b byte to validate
         * @return true if the byte is valid, false if it is not
         */
        boolean validateByte(int b);
    }
}

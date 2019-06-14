/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.comms;


import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.miurasystems.miuralibrary.enums.InterfaceType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;

/**
 * 'Generator' style class that yields ResponseMessages from a stream
 *
 * <p> Exists because the MPI protocol technically allows packets from
 * different NADs to interleave. It's highly unlikely to happen, but
 * it's possible.
 *
 * <p> e.g. MPI is sending back a large solicited chain of packets and
 * RPI also wants to send an unsolicited message at the same time.
 * The message from RPI might appear "in the middle" of the MPI
 * chain (or some other kind of "interleaved" configuration if
 * the RPI one is chained)
 *
 * <p> It also exists because Java doesn't have generators/co-routines/yield
 * and the streaming stuff is Java 8 only, which targeted versions of
 * Android don't have.
 *
 * <p> ResponseReader is a stateful object, and one needs to exist to
 * read ResponseMessages from a stream. Call {@link #nextResponse()}
 * to get the next available Response. nextResponse will keep
 * returning messages until it hits an error of some kind. Once it
 * does hit an error it will always return null and the entire
 * ResponseReader is considered "broken". You must make a new
 * one if you wish to use the class to read from the Stream.
 * (and most likely you'll want to disconnect/reconnect to MPI
 * before making a new ResponseReader)
 */
public class ResponseReader {

    /** SLF4J Logger */
    private static final Logger LOGGER = LoggerFactory.getLogger(ResponseReader.class);

    /** InputStream to read response messages from. */
    @NonNull
    private final InputStream mStream;

    /**
     * Lists of currently pending packets, one for each NAD.
     *
     * <p> The lists always exist and are never null.
     *
     * <p> Ideally a list will only be populated during a call to
     * nextResponse. But in the case of overlapping messages
     * from different NADs then the first NAD to completed
     * a message will "win" and the remaining packets will
     * hang around in mAllPendingPackets until the next time
     * nextResponse is called and it completed that NAD's message.
     */
    @NonNull
    private final EnumMap<InterfaceType, List<MpiPacket>> mAllPendingPackets;

    /**
     * Is the ResponseReader 'broken' or not?
     *
     * <p> Broken means: An error occurred whilst reading a packet or
     * building a message.
     *
     * <p> Once a ResponseReader is broken it is forever broken.
     */
    private boolean mIsBroken;

    /**
     * Construct a ResponseReader to read packets from the given stream.
     *
     * <p> Will now take "ownership" of the stream and be responsible for
     * reading from it. Don't call 'read()' on this stream after
     * ResponseReader owns it.
     *
     * @param stream The input stream to read the ResponseMessage from
     */
    public ResponseReader(@NonNull InputStream stream) {
        mStream = stream;

        mAllPendingPackets = new EnumMap<>(InterfaceType.class);
        for (InterfaceType e : InterfaceType.values()) {
            mAllPendingPackets.put(e, new ArrayList<MpiPacket>(1));
        }

        mIsBroken = false;
    }

    /**
     * Read a single ResponseMessage from an input stream. Blocks whilst doing so.
     *
     * <p> Will consume as many bytes (and therefore {@link MpiPacket}s) as
     * required to complete the ResponseMessage.
     *
     * <p> If two different NADs 'cross streams' then packets will be read from
     * both until one NAD has sent enough packets to fill out a complete
     * Response Message.
     *
     * <p> If nextResponse encounters any error whilst reading packets from
     * the stream or whilst constructing a ResponseMessage then it will return
     * null and will mark the ResponseReader as 'broken'.
     *
     * <p> Reads from a broken ResponseReader will return null, even if there
     * is data available in the stream. This is because such stream errors are
     * irrecoverable and require dealing with at a higher level
     * -- e.g. close and reopen connection.
     *
     * <p> Note that any pending packets that had yet to be turned into
     * messages are lost upon breaking.
     *
     * @return A valid ResponseMessage.
     * <p> If an errors occurs: null is returned and the ResponseReader is marked
     * as being broken.
     */
    @Nullable
    public ResponseMessage nextResponse() {

        if (mIsBroken) {
            LOGGER.warn("Reading from a broken reader!");
            return null;
        }

        while (true) {
            MpiPacket packet = MpiPacket.readFromStream(mStream);
            if (packet == null) {
                /*
                    If we fail to get a packet: give up.
                    We would fail due to a comms error or a packet error, neither of which we
                    can recover from here as it implies the stream is broken and future packets
                    will be out of sync and dodgy.
                */
                breakReader();
                return null;
            }
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("ResponseReader packet read: {}", Arrays.toString(packet.getBytes()));
            }

            InterfaceType nad = packet.getNodeAddress();
            if (!addToPendingPackets(nad, packet)) {
                // Can't recover from this kind of stream problem, so give up on
                // accumulating all NADs and bail out. It might be tempting to
                // try and just drop one nad, or just this packet, but there's
                // no real way to know what was actually intended to be sent,
                // given that we got a garbled stream, so it's best to
                // just stop everything.
                breakReader();
                return null;
            }

            if (!packet.isChained()) {
                ResponseMessage rm = buildResponse(nad);
                dropPendingPackets(nad);
                return rm;
            }
        }
    }

    /**
     * Add a packet from a NAD to the NAD's pending list.
     *
     * <p> Also checks to ensure that chains of messages are consistent.
     * If adding the new message would make a chain inconsistent,
     * it is not added and false is returned.
     *
     * <p> See {@link #solicitedIsInconsistent(MpiPacket, MpiPacket)}
     *
     * @param nad    Which Node Address the packet is from
     * @param packet The packet to add to the pending list.
     * @return true if the packet added without problems.
     * false if the packet wasn't added due to inconsistencies.
     */
    private boolean addToPendingPackets(InterfaceType nad, MpiPacket packet) {

        if (LOGGER.isWarnEnabled()) {
            for (InterfaceType tmp : mAllPendingPackets.keySet()) {
                if (tmp == nad) {
                    continue;
                }
                if (mAllPendingPackets.get(tmp).size() != 0) {
                    LOGGER.warn("Overlapping chained packets from differents NADs!");
                }
                break;
            }
        }

        List<MpiPacket> nadPackets = mAllPendingPackets.get(nad);
        if (nadPackets.size() > 0 &&
                solicitedIsInconsistent(nadPackets.get(0), packet)) {
            return false;
        }

        nadPackets.add(packet);
        return true;
    }

    /**
     * Create a ResponseMessage using the packets sent by a single NAD.
     *
     * @param nad Which NAD to use the packets from
     * @return A valid ResponseMessage
     */
    private ResponseMessage buildResponse(InterfaceType nad) {
        List<MpiPacket> readPackets = mAllPendingPackets.get(nad);

        byte[] apduBytes = MpiPacket.reconstructApdu(readPackets);
        boolean unsolicitedResponse = readPackets.get(0).isUnsolicited();
        InterfaceType target = readPackets.get(0).getNodeAddress();

        return new ResponseMessage(target, unsolicitedResponse, apduBytes);
    }

    /** Mark the reader as 'broken' and drop all current packets */
    private void breakReader() {
        for (InterfaceType nad : mAllPendingPackets.keySet()) {
            dropPendingPackets(nad);
        }
        mIsBroken = true;
    }

    /**
     * Drop all packets on a given NAD's pending list.
     *
     * @param nad The NAD to drop packets for.
     */
    private void dropPendingPackets(InterfaceType nad) {
        mAllPendingPackets.get(nad).clear();
    }

    /**
     * Tells if the 'solicited' status is consistent between two packets.
     *
     * If it's inconsistent, it logs the error and returns true.
     * Otherwise (if it's consistent) it returns false.
     *
     * @param firstPacket The first packet of a ResponseMessage.
     * @param packet      The most recently read packet of a ResponseMessage.
     * @return true if the packets solicited status are inconsistent
     * false if they are consistent
     */
    private static boolean solicitedIsInconsistent(MpiPacket firstPacket, MpiPacket packet) {
        boolean firstIsUnsolicited = firstPacket.isUnsolicited();
        boolean thisIsUnsolicited = packet.isUnsolicited();
        if (firstIsUnsolicited != thisIsUnsolicited) {
            // The Miura device should not be sending interleaved packets.
            // If it does: something crazy has happened to the comms line.
            String first = firstIsUnsolicited ? "unsolicited" : "solicited";
            String last = thisIsUnsolicited ? "unsolicited" : "solicited";
            LOGGER.warn("Recieved a {} packet in the middle of a {} chain?!", last, first);
            return true;
        }
        return false;
    }
}

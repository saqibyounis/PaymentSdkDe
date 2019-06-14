/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary;

import static com.miurasystems.miuralibrary.tlv.BinaryUtil.getBCD;
import static com.miurasystems.miuralibrary.tlv.BinaryUtil.intToUbyte;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.miurasystems.miuralibrary.api.objects.BatteryData;
import com.miurasystems.miuralibrary.api.objects.Capability;
import com.miurasystems.miuralibrary.api.objects.P2PEStatus;
import com.miurasystems.miuralibrary.api.objects.SoftwareInfo;
import com.miurasystems.miuralibrary.api.utils.SerialPortProperties;
import com.miurasystems.miuralibrary.comms.CommandApdu;
import com.miurasystems.miuralibrary.comms.ConnectionStateCallback;
import com.miurasystems.miuralibrary.comms.Connector;
import com.miurasystems.miuralibrary.comms.MpiProtocolSession;
import com.miurasystems.miuralibrary.comms.PollerMessage;
import com.miurasystems.miuralibrary.comms.ResponseMessage;
import com.miurasystems.miuralibrary.comms.UnsolicitedResponseCallback;
import com.miurasystems.miuralibrary.enums.BacklightSettings;
import com.miurasystems.miuralibrary.enums.CashDrawer;
import com.miurasystems.miuralibrary.enums.ChargingStatus;
import com.miurasystems.miuralibrary.enums.InterfaceType;
import com.miurasystems.miuralibrary.enums.OnlinePINError;
import com.miurasystems.miuralibrary.enums.RKIError;
import com.miurasystems.miuralibrary.enums.ResetDeviceType;
import com.miurasystems.miuralibrary.enums.SelectFileMode;
import com.miurasystems.miuralibrary.enums.StatusSettings;
import com.miurasystems.miuralibrary.enums.SystemLogMode;
import com.miurasystems.miuralibrary.enums.TransactionResponse;
import com.miurasystems.miuralibrary.enums.TransactionType;
import com.miurasystems.miuralibrary.events.ConnectionEventDispatcher;
import com.miurasystems.miuralibrary.events.MpiEvents;
import com.miurasystems.miuralibrary.events.UnsolicitedMessageEventDispatcher;
import com.miurasystems.miuralibrary.tlv.BinaryUtil;
import com.miurasystems.miuralibrary.tlv.Description;
import com.miurasystems.miuralibrary.tlv.HexUtil;
import com.miurasystems.miuralibrary.tlv.TLVObject;
import com.miurasystems.miuralibrary.tlv.TLVParser;
import com.miurasystems.miuralibrary.tlv.TLVTimeMiura;
import com.miurasystems.miuralibrary.tlv.Track2Data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

/**
 * Adapter that converts Unsolicited PollerMessages into Events
 *
 * <p>
 * Passed to {@link Connector#openSession(UnsolicitedResponseCallback, ConnectionStateCallback)
 * Connector.openSession()}. Converts the {@link PollerMessage}s
 * from {@link UnsolicitedResponseCallback} into the relevant
 * {@link com.miurasystems.miuralibrary.events.MpiEventPublisher#notifyListener(Object)
 * notifyListener()}
 * calls on an {@link MpiEvents} object.
 * </p>
 */
class UnsolicitedResponseAdapter implements UnsolicitedResponseCallback {

    /** SLF4J Logger */
    private static final Logger LOGGER = LoggerFactory.getLogger(UnsolicitedResponseAdapter.class);

    /** MpiEvents object to post events to. */
    private final MpiEvents mEvents;


    /**
     * Create a new adapter wrapping the given MpiEvents object
     *
     * @param events object to post events to
     */
    UnsolicitedResponseAdapter(MpiEvents events) {
        mEvents = events;
    }


    @Override
    public void handle(PollerMessage msg) {
        LOGGER.info("handle()");

        if (msg.response == null) throw new AssertionError();
        if (LOGGER.isTraceEnabled()) {
            byte[] body = msg.response.getBody();
            LOGGER.trace("nad:{}, status:{}, body:{}",
                    msg.response.getNodeAddress(),
                    msg.response.getStatusCode(),
                    BinaryUtil.parseHexString(body));
        }
        UnsolicitedMessageEventDispatcher.signalEvent(msg.response, mEvents);
    }
}

/**
 * Adapter that converts Connector's connection-state callbacks into Events
 *
 * <p>
 * Passed to {@link Connector#openSession(UnsolicitedResponseCallback, ConnectionStateCallback)
 * Connector.openSession()}. Converts the connection-state callbacks
 * from {@link ConnectionStateCallback} into the relevant
 * {@link com.miurasystems.miuralibrary.events.MpiEventPublisher#notifyListener(Object)
 * notifyListener()}
 * calls on an {@link MpiEvents} object.
 * </p>
 */
class ConnectionAdapter implements ConnectionStateCallback {

    /** SLF4J Logger */
    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectionAdapter.class);

    /** MpiEvents object to post events to. */
    private final MpiEvents mEvents;

    /**
     * Create a new adapter wrapping the given MpiEvents object
     *
     * @param events object to post events to
     */
    ConnectionAdapter(MpiEvents events) {
        mEvents = events;
    }

    @Override
    public void handle(boolean state) {
        LOGGER.info("handle(state:{})", state);
        ConnectionEventDispatcher.signalEvent(state, mEvents);
    }
}

/**
 * The primary Miura SDK class apps should use to interact with a Miura devices.
 *
 * <p>
 * MpiClient offers a Java-level API for interacting with Miura devices over a {@link Connector}.
 * E.g. {@code mpiClient.resetDevice(MPI, ResetDeviceType.Soft_Reset)} is equivalent to sending a
 * RESET_DEVICE command with p1 = 0x00, p2 = 0x0 (see 6.1.3 in the MPI API doc for details of
 * that command).
 * </p>
 *
 * <p>
 * MpiClient deals in connection "sessions". A single session is all the command/response pairs
 * sent between {@link #openSession()} and {@link #closeSession()}
 * (or more accurately, between a {@link MpiEvents#Connected} event and
 * a {@link MpiEvents#Disconnected} event, as the app might not call {@code closeSession()} but the
 * connection still might break).
 * These sessions match Miura devices' notion of state persistence for various commands.
 * e.g. if the connection breaks then a Miura PED will abort current transactions and reset
 * its display back to the idle state. An MpiClient starts with no active session
 * and <b>a new session must be {@link #openSession() opened}
 * before a command can be sent or an unsolicited message received</b>.
 * Only one session can be open at a time.
 * And after a session closes MpiClient can no longer send commands or
 * receive unsolicited messages until a new session is opened.
 * </p>
 *
 * <p>
 * All MpiClient methods are blocking and will wait for the command to be sent and a response to be
 * received before returning to the caller. An async wrapper around MpiClient is available in
 * {@link com.miurasystems.miuralibrary.api.executor.MiuraManager MiuraManager}.
 * An MpiClient instance is intended to live as long as your app does.
 * Multiple MpiClients can be instantiated if you desire to talk to multiple Miura devices.
 * </p>
 *
 * <p>
 * There are a variety of ways in which the methods can communicate success and failure. These
 * are based on the amount of information available from the commands.
 * </p>
 * <ul>
 * <li>
 * Commands with neither response data available nor defined error codes return a boolean.
 * {@code true} means success. {@code false} means failed.
 * No more failure information is available.
 * </li>
 * <li>
 * Commands with no response data available but with defined error codes return an enum,
 * with a "success" value enum available and the rest being error codes (e.g. {@link RKIError})
 * </li>
 * <li>
 * Commands with response data available but no defined error code return
 * an object (or a collection). Null means failure, anything else is success.
 * </li>
 * <li>
 * Commands with both response data available and defined error codes
 * returning a {@link Result} tuple/option type,
 * which contains a non-null object if success, or an error code if failed.
 * </li>
 * <li>
 * {@link #cardStatus(InterfaceType, boolean, boolean, boolean, boolean, boolean) CARD_STATUS}
 * is unusual and actually returns its data via an unsolicited message
 * (aka {@link MpiEvents#CardStatusChanged CardStatusChanged event}) and so therefore returns void.
 * </li>
 * </ul>
 *
 * <p>
 * Each MpiClient interacts with single connection, represented by a {@link Connector},
 * and on the other end of that connection there may be multiple Miura devices responding to
 * different {@link InterfaceType}s. (It could be just a single device, e.g. a Miura PED (MPI),
 * but it may be e.g. a Miura POSzle (RPI) with a Miura PED (MPI) attached as a peripheral).
 * Each method of MpiClient that interacts with devices takes a {@link InterfaceType} to
 * specify which device at the other end of the connection it is communicating with.
 * Any unsolicited messages will specify from which InterfaceType they come from.
 * </p>
 * <p>
 * The devices on the other end of a connection may wish to send messages to the app.
 * MpiClient will notify the app when these messages arrive by signalling the appropriate event
 * on the provided {@link MpiEvents} instance. MpiClient will also signal
 * {@link MpiEvents#Connected Connected} & {@link MpiEvents#Disconnected Disconnected}
 * events as they happen using the same MpiEvents object.
 * </p>
 *
 * <h4>A note on threading contexts</h4>
 * <p>
 * Due to the asynchronous nature of unsolicited messages MpiClient uses a separate thread
 * to read the Connector's InputStream, and <b>it's from this separate thread that the events
 * callbacks are invoked</b>. Thus, your event handlers need to be aware that they will not be
 * invoked on the thread that is interacting with the MpiClient methods.
 * </p>
 * <p>
 * An important consequence of this is that
 * <b>a minimum amount of code as possible should be put in an event listener</b>.
 * The more code that is in an event listener, the less responsive the input reader thread will be.
 * Apps absolutely <b>must not call more MpiClient methods from an event listener</b>,
 * as this will therefore be a deadlock.
 * See {@link MpiEvents} for more information about the threading model.
 * </p>
 *
 * <p>
 * MpiClient (and its use of MpiProtocolSession) is not thread-safe. Both classes
 * are abstractions over a single communication channel to a set of Miura Devices.
 * If the app wishes to send commands concurrently from multiple threads over the same ``MpiClient``
 * then that channel needs to be synchronised by the App, otherwise the order of commands
 * seen by the device will be chaotic. There would be no way for the thread-safe Client to know
 * which of the two concurrently sending commands you wish to execute first on the device.
 * </p>
 * <p>The only time that sending a command from a second thread would be desirable is in terms
 * of sending an abort whilst a transaction is currently in process. An extension of MpiClient is
 * available to account for that in the form of {@link MpiClientMTAbort}.
 * </p>
 */
public class MpiClient {

    /** SLF4J Logger */
    private static final Logger LOGGER = LoggerFactory.getLogger(MpiClient.class);

    // need API min level 19 to use StandardCharsets constants on Android,
    // so make our own instead.
    /** Constant for UTF-8 Charset */
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    /** Constant for US-ASCII Charset */
    private static final Charset US_ASCII = Charset.forName("US-ASCII");

    /** Constant for ISO_8859_1 Charset */
    private static final Charset ISO_8859_1 = Charset.forName("ISO-8859-1");

    /** MpiEvents object to post events to. */
    @NonNull
    private final MpiEvents mMpiEvents;

    /** Translates from @{link MpiProtocolSession}'s unsolicited response callbacks into events. */
    @NonNull
    private final UnsolicitedResponseCallback mUnsolicitedResponseAdapter;

    /** Translates from @{link MpiProtocolSession}'s connection state callbacks into events. */
    @NonNull
    private final ConnectionStateCallback mConnectionAdapter;

    /**
     * The Connector used to talk to Miura devices.
     * <p>
     * The communication channel where all the commands are sent and responses read from.
     * </p>
     */
    @NonNull
    private final Connector mConnector;

    /**
     * The current session.
     * <p>
     * Null if no session currently active. Commands and events cannot happen.
     * <br />
     * Non-null if there is a currently active session (or a broken one that we've yet to find out
     * is broken). Commands are allowed and events might happen.
     * </p>
     */
    @Nullable
    private MpiProtocolSession mSession;

    /**
     * Create a new MpiClient using the given connector as its communications channel.
     *
     * @param connector Uses the given Connector to interact with the Miura device at the other
     *                  end. This client now <b>exclusively</b> "owns" this Connector
     *                  (and therefore the underling data link).
     *                  <b>Do not</b> interact with the Connector or data-link after
     *                  constructing an MpiClient with it.
     * @param mpiEvents The MpiEvents object to publish events to.
     *                  Events will only begin to arrive whilst a session is active.
     *                  See {@link #openSession()}
     */
    public MpiClient(@NonNull Connector connector, @NonNull MpiEvents mpiEvents) {
        mMpiEvents = mpiEvents;
        mConnectionAdapter = new ConnectionAdapter(mpiEvents);
        mUnsolicitedResponseAdapter = new UnsolicitedResponseAdapter(mpiEvents);
        mConnector = connector;
        mSession = null;
    }

    /**
     * Open a new session on the Connector
     *
     * <p>
     * If the session is opened without error then a
     * {@link MpiEvents#Connected Connected} should occur.
     * </p>
     * <p>
     * If the Connector is not a usb-serial data link, then after the
     * {@link MpiEvents#Connected Connected} event there
     * should also be a {@link MpiEvents#DeviceStatusChanged DeviceStatusChanged}
     * event from the Miura device.
     * </p>
     *
     * @throws IOException If there was a problem opening the session
     */
    public void openSession() throws IOException {

        LOGGER.trace("openSession");
        mSession = mConnector.openSession(mUnsolicitedResponseAdapter, mConnectionAdapter);
        LOGGER.info("makeMpiProtocolSession returned ok. Session opened!");
    }

    /**
     * Closes a session on the Connector.
     *
     * <p>Will cause a {@link MpiEvents#Disconnected Disconnected} event.</p>
     */
    public void closeSession() {
        LOGGER.trace("closeSession:{}", mSession /*, new AssertionError("Just for stack trace")*/);
        mConnector.closeSession();
        mSession = null;
    }

    /**
     * Get the MpiEvents instance used by this MpiClient.
     *
     * <p>
     * Convenience method. Prevents users of this class having to pass around or store
     * both MpiEvents and MpiClient.
     * It's not recommended that you notify events on this MpiEvents. It exists for
     * reference equality purposes and registering new listeners etc.
     * </p>
     *
     * @return the MpiEvents used by this MpiClient.
     */
    @NonNull
    public MpiEvents getMpiEvents() {
        return mMpiEvents;
    }

    /**
     * Send a CommandApdu and receive an expected response on the given channel.
     *
     * @param interfaceType Which device to send it to
     * @param command       The command to send
     * @return The ResponseMessage received. Null if an error occurred.
     */
    @Nullable
    ResponseMessage sendAndReceive(
            @NonNull InterfaceType interfaceType,
            @NonNull CommandApdu command) {

        if (mSession == null) {
            return null;
        }
        try {
            mSession.sendCommandAPDU(interfaceType, command);
            return mSession.receiveResponse(interfaceType);
        } catch (IOException | InterruptedException e) {
            // .close will have been called, which sends disconnect event
            LOGGER.warn("sendAndReceive failed:{}", e.toString());
            return null;
        }
    }

    /**
     * Send a CommandApdu on the given channel.
     *
     * @param interfaceType Which device to send it to
     * @param command       The command to send
     * @return The session's command id for this command. -1 in case of error.
     */
    int sendCommand(
            @NonNull InterfaceType interfaceType,
            @NonNull CommandApdu command
    ) {
        if (mSession == null) {
            return -1;
        }
        try {
            return mSession.sendCommandAPDU(interfaceType, command);
        } catch (IOException e) {
            LOGGER.debug("sendComand failed:{}", e.toString());
            return -1;
        }
    }

    /**
     * Receive an expected response on the given channel.
     *
     * @param interfaceType Which device to receive from
     * @return The ResponseMessage received. Null if an error occurred.
     */
    @Nullable
    ResponseMessage receiveResponse(@NonNull InterfaceType interfaceType) {

        if (mSession == null) {
            return null;
        }
        try {
            return mSession.receiveResponse(interfaceType);
        } catch (IOException | InterruptedException e) {
            LOGGER.debug("receiveResponse failed:{}", e.toString());
            return null;
        }
    }

    /**
     * Send a CommandApdu, then a binary blob, then receive response on the given channel.
     * <p>
     * For use with STREAM_BINARY / {@link #streamBinary}.
     * </p>
     *
     * @param interfaceType Which device to send it to
     * @param command       The command to send
     * @param binary        The binary blob to send after the command
     * @param len           The length of `binary` to send
     * @return The ResponseMessage received. Null if an error occurred.
     */
    @Nullable
    private ResponseMessage sendAndReceiveBinary(
            @NonNull InterfaceType interfaceType,
            @NonNull CommandApdu command,
            @NonNull byte[] binary,
            int len) {

        if (mSession == null) {
            return null;
        }
        try {
            mSession.sendCommandAPDU(interfaceType, command);
            mSession.sendBinaryStream(interfaceType, binary, len);
            return mSession.receiveResponse(interfaceType);
        } catch (IOException | InterruptedException e) {
            // .close will have been called, which sends disconnect event
            LOGGER.debug("sendAndReceiveBinary failed:{}", e.toString());
            return null;
        }
    }

    @Nullable
    public ArrayList<Capability> getDeviceInfo(@NonNull InterfaceType interfaceType) {
        CommandApdu command = new CommandApdu(CommandType.Get_DeviceInfo);
        ResponseMessage rm = sendAndReceive(interfaceType, command);
        if (rm == null || !rm.isSuccess()) {
            return null;
        }

        ArrayList<Capability> capabilities = new ArrayList<>();
        List<TLVObject> tlvObjects = TLVParser.decode(rm.getBody());

        TLVObject tlvResponse = CommandUtil.firstMatch(tlvObjects, Description.Response_Data);
        if (tlvResponse == null) {
            return null;
        }

        //get list of TLV Objects with capabilities
        List<TLVObject> tlvTags = TLVParser.decode(tlvResponse.getRawData());
        for (TLVObject tlvTag : tlvTags) {

            //get Tag content - list of TLVs
            List<TLVObject> tlvTagContent = TLVParser.decode(tlvTag.getRawData());
            //search for key
            TLVObject tlvTagName = CommandUtil.firstMatch(tlvTagContent, Description.Identifier);

            //search for value
            TLVObject tlvTagValue = CommandUtil.firstMatch(tlvTagContent, Description.Version);

            //tlvTagName cannot be empty
            if (tlvTagName == null) {
                return null;
            }

            //parse to human format
            Capability capability;
            if (tlvTagValue == null) {
                capability = new Capability(HexUtil.bytesToString(tlvTagName.getRawData()));
            } else {
                capability = new Capability(
                        HexUtil.bytesToString(tlvTagName.getRawData()),
                        HexUtil.bytesToString(tlvTagValue.getRawData()));
            }

            capabilities.add(capability);
        }

        return capabilities;
    }

    @Nullable
    public Date systemClock(@NonNull InterfaceType interfaceType) {
        CommandApdu command = new CommandApdu(CommandType.System_Clock);
        ResponseMessage rm = sendAndReceive(interfaceType, command);

        if (rm == null || !rm.isSuccess()) {
            return null;
        }

        List<TLVObject> list = TLVParser.decode(rm.getBody());
        TLVObject tlvObjectDate = CommandUtil.firstMatch(list, Description.Date);
        TLVObject tlvObjectTime = CommandUtil.firstMatch(list, Description.Time);

        if (tlvObjectDate == null || tlvObjectTime == null) {
            return null;
        }

        String dateString = BinaryUtil.parseHexString(tlvObjectDate.getRawData());
        String timeString = BinaryUtil.parseHexString(tlvObjectTime.getRawData());

        //convert String into Date Object
        DateFormat dateTimePED = new SimpleDateFormat("yyMMddHHmmss");
        try {
            return dateTimePED.parse(dateString + timeString);
        } catch (ParseException e) {
            return null;
        }
    }

    public boolean systemClock(
            @NonNull InterfaceType interfaceType,
            @NonNull Date dateTime
    ) {
        TLVObject newDateTime = TLVTimeMiura.getTLVDateTime(dateTime);
        byte[] dataField = TLVParser.encode(newDateTime);
        CommandApdu command = new CommandApdu(CommandType.System_Clock, dataField);
        ResponseMessage rm = sendAndReceive(interfaceType, command);
        return rm != null && rm.isSuccess();
    }

    public boolean displayText(
            @NonNull InterfaceType interfaceType,
            @NonNull String text,
            boolean isFourRow, boolean isBacklightOn, boolean isUTF8Encoding
    ) {
        int p1 = 0x00;
        if (!isFourRow) p1 = 0x01;

        int p2 = 0x00;
        if (isBacklightOn) p2 |= 0x01;
        if (isUTF8Encoding) p2 |= 0x80;

        Charset charset = isUTF8Encoding ? UTF_8 : US_ASCII;
        byte[] dataField = text.getBytes(charset);

        CommandApdu command = new CommandApdu(CommandType.Display_Text, p1, p2, dataField);
        ResponseMessage rm = sendAndReceive(interfaceType, command);
        return rm != null && rm.isSuccess();
    }

    @Nullable
    public BatteryData batteryStatus(@NonNull InterfaceType interfaceType, boolean intoSleep) {
        int p1 = intoSleep ? 1 : 0;
        CommandApdu command = new CommandApdu(CommandType.Battery_Status, p1, 0);

        ResponseMessage rm = sendAndReceive(interfaceType, command);
        if (rm == null || !rm.isSuccess()) {
            return null;
        }

        List<TLVObject> tlvObjects = TLVParser.decode(rm.getBody());
        TLVObject tlvBatteryLevel = CommandUtil.firstMatch(tlvObjects, Description.Battery_Status);
        TLVObject tlvChargingStatus = CommandUtil.firstMatch(tlvObjects,
                Description.Charging_Status);

        if (tlvBatteryLevel == null || tlvChargingStatus == null) {
            LOGGER.warn("Can't convert battery data TLV?");
            return null;
        }

        byte chargingStatusByte = tlvChargingStatus.getRawData()[0];
        ChargingStatus status = ChargingStatus.getByValue(chargingStatusByte);
        if (status == null) {
            LOGGER.warn("Unknown ChargingStatus enum? {}", chargingStatusByte);
            return null;
        }

        byte batteryLevelByte = tlvBatteryLevel.getRawData()[0];
        int percent = BinaryUtil.ubyteToInt(batteryLevelByte);

        return new BatteryData(status, percent);
    }

    public void cardStatus(
            @NonNull InterfaceType interfaceType,
            boolean enableUnsolicited,
            boolean enableAtr,
            boolean enableTrack1,
            boolean enableTrack2,
            boolean enableTrack3
    ) {
        if (mSession == null) {
            return;
        }
        int p1 = 0x00;
        if (enableUnsolicited) p1 |= 0x01;
        if (enableAtr) p1 |= 0x02;
        if (enableTrack1) p1 |= 0x04;
        if (enableTrack2) p1 |= 0x08;
        if (enableTrack3) p1 |= 0x10;

        CommandApdu command = new CommandApdu(CommandType.Card_Status, p1, 0);
        // ResponseMessage rm = sendAndReceive(interfaceType, command);
        // return rm != null && rm.isSuccess();

        // cardStatus returns via an unsolicited message, so only send, no receive.
        try {
            mSession.sendCommandAPDU(interfaceType, command);
        } catch (IOException e) {
            LOGGER.info("cardStatus failed:{}", e.toString());
        }
    }

    @Nullable
    public SoftwareInfo resetDevice(
            @NonNull InterfaceType interfaceType,
            @NonNull ResetDeviceType type
    ) {
        // that case.
        if (interfaceType == InterfaceType.RPI
                && type == ResetDeviceType.Clear_Files_And_Reinitialise_MSD) {
            throw new IllegalArgumentException(
                    "RPI cannot reset device with clear files and reinitialize MSD argument");
        }

        int p1 = type.getType();
        CommandApdu command = new CommandApdu(CommandType.Reset_Device, p1, 0);
        ResponseMessage rm = sendAndReceive(interfaceType, command);
        if (rm == null || !rm.isSuccess()) {
            return null;
        }

        /* Example response.
        e1 L:30 v: [
            t:9f1e l:08 v:3034303030323033 "04000203"
            t:ef l:11 v:
                t:df0d l:08 v:4d3130302d525049 "M100-RPI"
                t:df7f l:03 v:312d31 "1-2"
            t:ef l:10
                t:df0d l:07 v:4d3130302d4f53 "M100-OS"
                t:df7f l:03 v:312d36 "1-6"

            for RPI 1-1 that last df7f will be:
                t:df0d l:03 v:312d36 "1-6"
        ]
        */

        List<TLVObject> topList = TLVParser.decode(rm.getBody());
        TLVObject tlvSerialNumber = CommandUtil.firstMatch(
                topList, Description.Interface_Device_Serial_Number);
        if (tlvSerialNumber == null) {
            return null;
        }

        TLVObject tlvMpiInfo =
                CommandUtil.searchTagValue(topList, Description.Software_Information, 1);
        TLVObject tlvOsInfo =
                CommandUtil.searchTagValue(topList, Description.Software_Information, 2);

        if (tlvMpiInfo == null || tlvOsInfo == null) {
            return null;
        }

        // Parse Mpi info
        List<TLVObject> mpiList = tlvMpiInfo.getConstrustedTLV();
        TLVObject tlvMpiType = CommandUtil.firstMatch(mpiList, Description.Identifier);
        TLVObject tlvMpiVer = CommandUtil.firstMatch(mpiList, Description.Version);
        if (tlvMpiType == null || tlvMpiVer == null) {
            return null;
        }
        String mpiType = tlvMpiType.getData();
        String mpiVersion = tlvMpiVer.getData();

        // Parse OS info
        List<TLVObject> osList = tlvOsInfo.getConstrustedTLV();
        TLVObject tlvOsType = CommandUtil.firstMatch(osList, Description.Identifier);
        TLVObject tlvOsVer;
        if (mpiType.equals("M100-RPI") && mpiVersion.equals("1-1")) {
            // JIRA MSDK-233: RPI 1-1 has a bug where it sends two DF0Ds instead of a {DF0D, D7F7}
            tlvOsVer = CommandUtil.searchTagValue(osList, Description.Identifier, 2);
        } else {
            tlvOsVer = CommandUtil.firstMatch(osList, Description.Version);
        }
        if (tlvOsType == null || tlvOsVer == null) {
            return null;
        }

        String serialNumber, osType, osVersion;
        serialNumber = tlvSerialNumber.getData();
        osType = tlvOsType.getData();
        osVersion = tlvOsVer.getData();

        return new SoftwareInfo(serialNumber, mpiType, mpiVersion, osType, osVersion);
    }

    @Nullable
    public HashMap<String, String> getConfiguration() {
        CommandApdu command = new CommandApdu(CommandType.Get_Configuration);
        ResponseMessage rm = sendAndReceive(InterfaceType.MPI, command);
        if (rm == null || !rm.isSuccess()) {
            return null;
        }

        List<TLVObject> list = TLVParser.decode(rm.getBody());
        HashMap<String, String> versionMap = new HashMap<>();

        for (int i = 1; ; i++) {
            TLVObject tlvIdentifier = CommandUtil.searchTagValue(list, Description.Identifier, i);
            TLVObject tlvVersion = CommandUtil.searchTagValue(list, Description.Version, i);
            if (tlvIdentifier != null && tlvVersion != null) {
                versionMap.put(tlvIdentifier.getData(), tlvVersion.getData());
            } else {
                break;
            }
        }

        return versionMap;
    }

    public boolean keyboardStatus(
            @NonNull InterfaceType interfaceType,
            @NonNull StatusSettings statusSetting,
            @NonNull BacklightSettings backlightSetting
    ) {
        int p1 = statusSetting.getValue();
        int p2 = backlightSetting.getValue();
        CommandApdu command = new CommandApdu(CommandType.Keyboard_Status, p1, p2);
        ResponseMessage rm = sendAndReceive(interfaceType, command);
        return rm != null && rm.isSuccess();
    }

    public int selectFile(
            @NonNull InterfaceType interfaceType,
            @NonNull SelectFileMode mode,
            @NonNull String fileName
    ) {
        byte[] ascii = fileName.getBytes(US_ASCII);

        int p1 = mode.getValue();
        CommandApdu command = new CommandApdu(CommandType.Select_File, p1, 0x0, ascii);
        ResponseMessage rm = sendAndReceive(interfaceType, command);
        if (rm == null || !rm.isSuccess()) {
            return -1;
        }

        List<TLVObject> list = TLVParser.decode(rm.getBody());
        TLVObject tlvFileSize = CommandUtil.firstMatch(list, Description.File_Size);
        if (tlvFileSize == null) {
            return -1;
        }

        try {
            byte[] rawData = tlvFileSize.getRawData();
            if (rawData.length == 0) {
                return 0;
            }
            String rawDataString = BinaryUtil.parseHexString(rawData);
            return Integer.parseInt(rawDataString, 16);
        } catch (NumberFormatException ignore) {
            return -1;
        }
    }

    public boolean streamBinary(
            @NonNull InterfaceType interfaceType,
            boolean needMd5sum,
            byte[] binary,
            int offset,
            int size,
            int timeout
    ) {
        LOGGER.debug(
                "streamBinary(nad:{}, md5? {}, binary len:{}, offset: {}, size:{}, timeout:{}",
                interfaceType, needMd5sum, binary.length, offset, size, timeout);

        int p1 = 0x00;
        if (needMd5sum) {
            p1 = 0x01;
        }

        byte[] streamOffset = TLVParser.encode(Description.Stream_Offset,
                new byte[]{
                        (byte) ((offset >> 16) & 0xFF),
                        (byte) ((offset >> 8) & 0xFF),
                        (byte) (offset & 0xFF)
                });
        byte[] streamSize = TLVParser.encode(Description.Stream_Size,
                new byte[]{
                        (byte) ((size >> 16) & 0xFF),
                        (byte) ((size >> 8) & 0xFF),
                        (byte) (size & 0xFF)
                });
        byte[] streamTimeout = TLVParser.encode(Description.Stream_timeout,
                new byte[]{(byte) (timeout & 0xFF)});

        byte[] commandData = CommandUtil.copyArray(streamOffset, streamSize);
        commandData = CommandUtil.copyArray(commandData, streamTimeout);
        commandData = TLVParser.encode(Description.Command_Data, commandData);

        CommandApdu command = new CommandApdu(CommandType.Stream_Binary, p1, 0x0, commandData);
        ResponseMessage rm = sendAndReceiveBinary(interfaceType, command, binary, size);
        return rm != null && rm.isSuccess();
    }

    public boolean systemLog(
            @NonNull InterfaceType interfaceType,
            @NonNull SystemLogMode mode
    ) {
        int p1 = mode.getValue();
        CommandApdu command = new CommandApdu(CommandType.System_Log, p1, 0x0);
        ResponseMessage rm = sendAndReceive(interfaceType, command);
        return rm != null && rm.isSuccess();
    }

    @Nullable
    public ResponseMessage readBinary(
            @NonNull InterfaceType interfaceType,
            int fileSize,
            int offset,
            int size
    ) {
        if (size > 0xFC) {
            size = 0xFC;
        }

        if (fileSize < offset + size) {
            size = fileSize - offset;
            LOGGER.info("ReadBinary Error Log : offset > fileSize");
        }

        int p1;
        int p2;
        byte[] dataField;

        if (offset <= 0x7FFF) {
            p1 = ((offset >> 8) & 0x7F);
            p2 = (offset & 0xFF);
            dataField = null;
        } else if (offset <= 0x7FFFFF) {
            p1 = (0x80 | ((offset >> 16) & 0x7F));
            p2 = ((offset >> 8) & 0xFF);
            dataField = new byte[]{(byte) (offset & 0xFF)};
        } else {
            throw new IllegalArgumentException("READ_BINARY max address size is 23 bits");
        }

        CommandApdu command = new CommandApdu(CommandType.Read_Binary, p1, p2, dataField, size);
        return sendAndReceive(interfaceType, command);
    }

    @NonNull
    public Result<OnlinePinResult, OnlinePINError> onlinePin(
            @NonNull InterfaceType interfaceType,
            int amountInPennies,
            int currencyCode,
            @NonNull Track2Data maskedTrack2Data,
            @NonNull String applicationLabel
    ) {
        CommandApdu command = makeOnlinePinCommand(
                amountInPennies, currencyCode, maskedTrack2Data, applicationLabel);
        ResponseMessage rm = sendAndReceive(interfaceType, command);
        return processOnlinePinResult(rm);
    }

    @NonNull
    static CommandApdu makeOnlinePinCommand(
            int amountInPennies,
            int currencyCode,
            @NonNull Track2Data maskedTrack2Data,
            @NonNull String applicationLabel
    ) {
        byte[] amountBytes = getBCD(amountInPennies, 6);
        byte[] currencyBytes = getBCD(currencyCode, 2);

        ArrayList<TLVObject> tlvObjects = new ArrayList<>();
        TLVObject tlvAmount = new TLVObject(Description.Amount_Authorised_Numeric, amountBytes);
        TLVObject tlvAppLabel = new TLVObject(
                Description.Application_Label,
                BinaryUtil.parseHexBinary(HexUtil.asciiToHex(applicationLabel)));
        TLVObject tlvMaskedTrack2Data = new TLVObject(
                Description.Masked_Track_2, maskedTrack2Data.getRaw());
        TLVObject tlvCurrencyCode = new TLVObject(
                Description.Transaction_Currency_Code, currencyBytes);

        tlvObjects.add(tlvAmount);
        tlvObjects.add(tlvAppLabel);
        tlvObjects.add(tlvCurrencyCode);
        tlvObjects.add(tlvMaskedTrack2Data);

        TLVObject tlvTransaction = new TLVObject(Description.Command_Data, tlvObjects);
        byte[] dataField = TLVParser.encode(tlvTransaction);

        return new CommandApdu(CommandType.Online_PIN, 0x01, 0x0, dataField);
    }

    @NonNull
    static Result<OnlinePinResult, OnlinePINError> processOnlinePinResult(
            @Nullable ResponseMessage rm
    ) {
        if (rm == null) {
            return new Result.Error<>(OnlinePINError.INTERNAL_ERROR);
        }

        byte[] body = rm.getBody();
        if (body.length == 0) {
            /*Check error code. These are undocumented in V1-45*/
            int sw1 = BinaryUtil.ubyteToInt(rm.getSw1());
            int sw2 = BinaryUtil.ubyteToInt(rm.getSw2());
            LOGGER.debug(String.format("SW1: 0x%02X SW2: 0x%02X", sw1, sw2));

            Result.Error<OnlinePinResult, OnlinePINError> error;
            if ((sw1 == 0x9F) && (sw2 == 0xE0)) {
                error = new Result.Error<>(OnlinePINError.NO_PIN_KEY);
            } else if ((sw1 == 0x9F) && (sw2 == 0x0F)) {
                error = new Result.Error<>(OnlinePINError.INVALID_PARAM);
            } else {
                error = new Result.Error<>(OnlinePINError.INTERNAL_ERROR);
            }
            LOGGER.debug(error.getError().name());
            return error;
        }

        // If the response message contains a single byte, this indicates an error condition.
        if (body.length == 1) {

            int singleByte = BinaryUtil.ubyteToInt(body[0]);
            if (singleByte == Description.Payment_Cancel_Or_PIN_Entry_Timeout.getTag()) {
                return new Result.Success<>(
                        new OnlinePinResult(OnlinePinResultType.CancelOrTimeout));
            }

            if (singleByte == Description.Payment_User_Bypassed_PIN.getTag()) {
                return new Result.Success<>(
                        new OnlinePinResult(OnlinePinResultType.BypassedPinEntry));
            }

            if (singleByte == Description.Payment_Internal_1.getTag()
                    || singleByte == Description.Payment_Internal_2.getTag()
                    || singleByte == Description.Payment_Internal_3.getTag()) {
                return new Result.Error<>(OnlinePINError.INTERNAL_ERROR);
            }
            return new Result.Error<>(OnlinePINError.INTERNAL_ERROR);
        }

        List<TLVObject> bodyTlvObjects = TLVParser.decode(body);

        TLVObject tlvOnlinePinData = CommandUtil.firstMatch(
                bodyTlvObjects, Description.Online_PIN_Data);
        TLVObject tlvOnlinePINKSN = CommandUtil.firstMatch(
                bodyTlvObjects, Description.Online_PIN_KSN);
        if (tlvOnlinePinData == null || tlvOnlinePINKSN == null) {
            return new Result.Error<>(OnlinePINError.INTERNAL_ERROR);
        }

        OnlinePinResult result = new OnlinePinResult(
                tlvOnlinePinData.getRawData(), tlvOnlinePINKSN.getRawData());
        return new Result.Success<>(result);
    }


    /**
     * Issues a START_TRANSACTION command to the Miura Device.
     *
     * <p>
     * See MPI API doc '6.31 START TRANSACTION' for more information on the command.
     * </p>
     *
     * <p>
     * Like other device-command methods in MpiClient, this method blocks the current thread
     * whilst it sends the command and waits for a response. Unlike other methods it might
     * <b>block for a considerable amount of time</b>. e.g. if the device is waiting for
     * user input and has yet to send a response to a the SDK.
     * </p>
     *
     * @param interfaceType   The interface to send/receive to/from.
     * @param transactionType The type of the transaction
     * @param amountInPennies The amount, in pence, of the transaction
     * @param currencyCode    The ISO 4217 code. Overrides default in emv.cfg
     * @return A Result of:
     * <ol>
     * <li>success: the body of the response. These are TLV data that needs to be decoded using
     * the classes in the {@link com.miurasystems.miuralibrary.tlv} package.
     * e.g. {@link TLVParser#decode(byte[])}
     * </li>
     * <li>
     * error: Reason for transaction failure.
     * </li>
     * </ol>
     */
    @NonNull
    public Result<byte[], TransactionResponse> startTransaction(
            @NonNull InterfaceType interfaceType,
            @NonNull TransactionType transactionType,
            int amountInPennies,
            int currencyCode
    ) {
        CommandApdu command = makeStartTransactionCommand(
                transactionType, amountInPennies, currencyCode);
        ResponseMessage rm = sendAndReceive(interfaceType, command);
        return processTransactionResponse(rm);
    }

    @NonNull
    public Result<byte[], TransactionResponse> startContactlessTransaction(
            @NonNull InterfaceType interfaceType,
            @NonNull TransactionType transactionType,
            int amountInPennies,
            int currencyCode,
            @Nullable String languagePreference
    ) {
        CommandApdu command = makeStartContactlessTransactionCommand(
                transactionType, amountInPennies, currencyCode, languagePreference);
        ResponseMessage rm = sendAndReceive(interfaceType, command);

        return processTransactionResponse(rm);
    }

    /**
     * Issues an ABORT command to the Miura device
     *
     * <p>
     * Causes the device to 'Exit any wait loops, cancel any transactions in progress and
     * return to the idle state keeping any communications session active'.
     * (from MPI API doc '6.34 ABORT').
     * </p>
     *
     * <p>
     * MpiClient is not multi-thread safe and therefore ABORT cannot be sent if
     * another thread is currently blocked in a transaction method.
     * See {@link MpiClientMTAbort#abortTransaction(InterfaceType)} for a method that can
     * cause a mid-transaction ABORT.
     * </p>
     *
     * @param interfaceType The device to send the ABORT command to
     * @return true if the abort worked ok, false is the command failed in the SDK or on the device
     */
    public boolean abortTransaction(@NonNull InterfaceType interfaceType) {
        CommandApdu command = new CommandApdu(CommandType.Abort);
        ResponseMessage rm = sendAndReceive(interfaceType, command);
        return rm != null && rm.isSuccess();
    }

    @Nullable
    public P2PEStatus p2peStatus(@NonNull InterfaceType interfaceType) {
        CommandApdu command = new CommandApdu(CommandType.P2PE_Status);
        ResponseMessage rm = sendAndReceive(interfaceType, command);
        if (rm == null || !rm.isSuccess()) {
            return null;
        }

        List<TLVObject> list = TLVParser.decode(rm.getBody());
        TLVObject tlvFileSize = CommandUtil.firstMatch(list, Description.P2PE_Status);

        if (tlvFileSize == null) {
            return null;
        }
        byte[] rawData = tlvFileSize.getRawData();

        P2PEStatus p2peStatus = new P2PEStatus();
        p2peStatus.isInitialised = (rawData[0] & (0x01)) > 0;
        p2peStatus.isPINReady = (rawData[0] & (0x02)) > 0;
        p2peStatus.isSREDReady = (rawData[0] & (0x04)) > 0;
        return p2peStatus;
    }

    public boolean p2peInitialise(@NonNull InterfaceType interfaceType) {
        CommandApdu command = new CommandApdu(CommandType.P2PE_Initialise);
        ResponseMessage rm = sendAndReceive(interfaceType, command);
        return rm != null && rm.isSuccess();
    }

    @NonNull
    public RKIError p2peImport(@NonNull InterfaceType interfaceType) {
        CommandApdu command = new CommandApdu(CommandType.P2PE_Import);
        ResponseMessage rm = sendAndReceive(interfaceType, command);
        if (rm == null) {
            return RKIError.RkiMiuraInternalError;
        }
        if (rm.isSuccess()) {
            return RKIError.NoError;
        } else {
            if (BinaryUtil.ubyteToInt(rm.getSw1()) != 0x9f) {
                LOGGER.warn("Unknown error code for p2peImport?");
                return RKIError.RkiMiuraInternalError;
            }
            return RKIError.valueOf(rm.getSw2());
        }
    }

    @NonNull
    public Result<byte[], TransactionResponse> continueTransaction(
            @NonNull InterfaceType interfaceType,
            @NonNull TLVObject transactionInfo
    ) {
        byte[] dataField = TLVParser.encode(transactionInfo);
        CommandApdu command = new CommandApdu(CommandType.Continue_Transaction, dataField);
        ResponseMessage rm = sendAndReceive(interfaceType, command);
        return processTransactionResponse(rm);
    }

    @Nullable
    public ArrayList<String> peripheralStatusCommand() {
        CommandApdu command = new CommandApdu(CommandType.Peripheral_Status);
        ResponseMessage rm = sendAndReceive(InterfaceType.RPI, command);

        if (rm == null || !rm.isSuccess()) {
            return null;
        }

        ArrayList<String> peripheralTypes = new ArrayList<>();
        if (rm.getBody().length > 1) {
            List<TLVObject> type = TLVParser.decode(rm.getBody());

            for (int i = 1; ; i++) {
                TLVObject items = CommandUtil.searchTagValue(type, Description.Identifier, i);
                if (items != null) {
                    peripheralTypes.add(String.valueOf(items.getData()));
                } else {
                    break;
                }
            }
        }
        return peripheralTypes;
    }

    public boolean barcodeStatus(
            @NonNull InterfaceType interfaceType,
            boolean codeReporting
    ) {
        int p1 = codeReporting ? 1 : 0;
        CommandApdu command = new CommandApdu(CommandType.Bar_Code_Scanner_Status, p1, 0x0);
        ResponseMessage rm = sendAndReceive(interfaceType, command);
        return rm != null && rm.isSuccess();
    }

    public boolean spoolText(
            @NonNull InterfaceType interfaceType,
            @NonNull String text
    ) {

        int p1 = 0x00;  /*Add to the spool*/
        int p2 = 0x00;  /*Data field is text to be printed*/
        byte[] dataField = text.getBytes(UTF_8);

        CommandApdu command = new CommandApdu(CommandType.Spool_print, p1, p2, dataField);
        ResponseMessage rm = sendAndReceive(interfaceType, command);
        return rm != null && rm.isSuccess();
    }

    public boolean spoolImage(
            @NonNull InterfaceType interfaceType,
            @NonNull String fileName
    ) {
        int p1 = 0x00;  /*Add to the spool*/
        int p2 = 0x01;  /*Data is a file name of a bitmap to be printed*/
        byte[] dataField = fileName.getBytes(UTF_8);

        CommandApdu command = new CommandApdu(CommandType.Spool_print, p1, p2, dataField);
        ResponseMessage rm = sendAndReceive(interfaceType, command);
        return rm != null && rm.isSuccess();
    }

    public boolean spoolPrint(@NonNull InterfaceType interfaceType) {
        int p1 = 0x02;  /*Print the spool*/
        CommandApdu command = new CommandApdu(CommandType.Spool_print, p1, 0x00);
        ResponseMessage rm = sendAndReceive(interfaceType, command);
        return rm != null && rm.isSuccess();
    }

    public boolean printESCPOScommand(
            @NonNull InterfaceType interfaceType,
            @NonNull String text
    ) {
        byte[] dataField = text.getBytes(ISO_8859_1);
        CommandApdu command = new CommandApdu(CommandType.print_ESCPOS, dataField);
        ResponseMessage rm = sendAndReceive(interfaceType, command);
        return rm != null && rm.isSuccess();
    }

    @Deprecated
    public boolean printText(
            @NonNull InterfaceType interfaceType,
            @NonNull String text,
            boolean wait
    ) {

        int p1 = wait ? 1 : 0;
        Charset charset = (interfaceType == InterfaceType.MPI) ? US_ASCII : UTF_8;
        byte[] dataField = text.getBytes(charset);

        CommandApdu command = new CommandApdu(CommandType.Print_Text, p1, 0x0, dataField);
        ResponseMessage rm = sendAndReceive(interfaceType, command);
        return rm != null && rm.isSuccess();
    }

    @Deprecated
    public boolean printImage(
            @NonNull InterfaceType interfaceType,
            @NonNull String image
    ) {

        byte[] dataField = image.getBytes(US_ASCII);
        CommandApdu command = new CommandApdu(CommandType.Print_Image, dataField);
        ResponseMessage rm = sendAndReceive(interfaceType, command);
        return rm != null && rm.isSuccess();
    }

    /**
     * Queries status of the cash drawer, and optionally requests the cash drawer to open.
     */
    @Nullable
    public CashDrawer cashDrawer(boolean openDrawer) {
        int p1 = openDrawer ? 1 : 0;
        CommandApdu command = new CommandApdu(CommandType.Cash_Drawer, p1, 0);
        ResponseMessage rm = sendAndReceive(InterfaceType.RPI, command);

        if (rm != null && rm.isSuccess()) {
            byte responseBody = rm.getBody()[0];
            return CashDrawer.getByValue(responseBody);
        } else {
            return null;
        }
    }

    public boolean printerSledStatus(
            @NonNull InterfaceType interfaceType,
            boolean printerSledStatusEnabled
    ) {

        int p1 = printerSledStatusEnabled ? 1 : 0;
        CommandApdu command = new CommandApdu(CommandType.Printer_Status, p1, 0x0);
        ResponseMessage rm = sendAndReceive(interfaceType, command);
        return rm != null && rm.isSuccess();
    }

    @Nullable
    public HashMap<String, String> getBluetoothInfo() {
        CommandApdu command = new CommandApdu(CommandType.Bluetooth_Control);
        ResponseMessage rm = sendAndReceive(InterfaceType.RPI, command);

        if (rm == null || !rm.isSuccess()) {
            return null;
        }

        List<TLVObject> list = TLVParser.decode(rm.getBody());
        HashMap<String, String> blueInfo = new HashMap<>();

        for (int i = 1; ; i++) {
            TLVObject bt_name = CommandUtil.searchTagValue(list, Description.Identifier, i);
            TLVObject bt_address = CommandUtil.searchTagValue(list, Description.Version, i);
            if (bt_name != null && bt_address != null) {
                blueInfo.put(bt_name.getData(), bt_address.getData());
            } else {
                break;
            }
        }
        return blueInfo;
    }

    public boolean setSerialPort(@NonNull SerialPortProperties serialPortProperties) {
        byte[] data = new byte[7];

        /*Assign the variables for the serial port settings.*/
        data[0] = serialPortProperties.getBaudRate().getValue();
        data[1] = serialPortProperties.getCharSize().getValue();
        data[2] = serialPortProperties.isParityEnabled() ? (byte) 0x01 : (byte) 0x00;
        data[3] = serialPortProperties.isOddParity() ? (byte) 0x01 : (byte) 0x00;
        data[4] = serialPortProperties.getStopBits().getValue();
        data[5] = serialPortProperties.isHwFlowControlEnabled() ? (byte) 0x01 : (byte) 0x00;
        data[6] = serialPortProperties.isSwFlowControlEnabled() ? (byte) 0x01 : (byte) 0x00;
        CommandApdu command = new CommandApdu(CommandType.Setup_USB_Serial_Adaptor, data);
        ResponseMessage rm = sendAndReceive(InterfaceType.RPI, command);
        return rm != null && rm.isSuccess();
    }

    public boolean sendDataToSerialPort(@NonNull byte[] data) {
        CommandApdu command = new CommandApdu(CommandType.Send_USB_Serial_Data, data);
        ResponseMessage rm = sendAndReceive(InterfaceType.RPI, command);
        return rm != null && rm.isSuccess();
    }

    /**
     *
     * <p>
     * See MPI API doc '6.25 GET DYNAMIC TIP' for more information on the command.
     * </p>
     *
     * @param displayedAmountIncludesTotal true = the amount on screen is tip+total.
     *                                     false = the amount on screen is how much the tip is
     * @param backlightOn Enable key pad backlight
     * @param percentages List of percents to display. Percents are in range [0, 253].
     *                    254 = displays blank line. 255 = "Other".
     *                    Can have [1, 4] percentages in the list.
     * @param tipTemplate Line of tip-templates.cfg to use when displaying options
     * @param amountInPennies The amount, in pennies, to dynamically generate tip from
     * @param currencyCode      ISO 4217 currency code to be used when the amount is displayed.
     *                          See '14.9 Numeric-entry.cfg' section in the MPI API guide
     *                          for acceptable
     * @param currencyExponent  ISO 4217 currency exponent used to format the amount
     * @return Either:
     * <ol>
     * <li>Success: The index of percentages that was chosen.
     *      (Will be >=0 && < pecentages.size()</li>
     * <li>Error: Relevant GetNumericDataError</li>
     * </ol>
     */
    public Result<Integer, GetNumericDataError> getDynamicTip(
            boolean displayedAmountIncludesTotal,
            boolean backlightOn,
            @NonNull List<Integer> percentages,
            int tipTemplate,
            int amountInPennies,
            int currencyCode,
            int currencyExponent
    ) {

        // region parameter-validation
        int percentCount = percentages.size();
        if (percentCount < 1 || percentCount > 4) {
            throw new IllegalArgumentException(String.format(
                    "percentages must have [1,4] elements, not '%d'",
                    percentCount));
        }
        for (int pc : percentages) {
            if (pc < 0 || pc > 255) {
                throw new IllegalArgumentException(String.format(
                        "A percentage must be in range [0, 253]%%, " +
                                "or be 254 (blank line), 255 ('Other'), not '%d'",
                        percentCount));
            }
        }
        if (tipTemplate < 1 || tipTemplate > 255) {
            throw new IllegalArgumentException(String.format(
                    "Tip template must be in range [1, 255], not %d ",
                    tipTemplate));
        }
        // endregion parameter-validation

        int p1 = displayedAmountIncludesTotal ? 0x0 : 0x1;
        int p2 = backlightOn ? 0x1 : 0x0;

        ArrayList<TLVObject> e0List = new ArrayList<>(7);

        byte[] percentBytes = new byte[4];
        int idx;
        for (idx = 0; idx < percentCount; idx++) {
            percentBytes[idx] = intToUbyte(percentages.get(idx));
        }
        //noinspection ForLoopWithMissingComponent
        for (; idx < 4; idx++) {
            percentBytes[idx] = intToUbyte(254);
        }

        TLVObject tlvSecurePrompt = new TLVObject(
                Description.Dynamic_Tip_Percentages,
                percentBytes
        );
        e0List.add(tlvSecurePrompt);

        TLVObject tlvTemplate = new TLVObject(
                Description.Dynamic_Tip_Template,
                new byte[]{intToUbyte(tipTemplate)}
        );
        e0List.add(tlvTemplate);

        byte[] currencyBytes = getBCD(currencyCode, 2);
        TLVObject tlvCurrencyCode = new TLVObject(
                Description.Transaction_Currency_Code, currencyBytes
        );
        e0List.add(tlvCurrencyCode);

        TLVObject tlvCurrencyExponent = new TLVObject(
                Description.Transaction_Currency_Exponent,
                new byte[]{intToUbyte(currencyExponent)}
        );
        e0List.add(tlvCurrencyExponent);

        byte[] amountBytes = getBCD(amountInPennies, 6);
        TLVObject tlvAmount = new TLVObject(
                Description.Amount_Authorised_Numeric,
                amountBytes
        );
        e0List.add(tlvAmount);

        TLVObject tlvTransaction = new TLVObject(Description.Command_Data, e0List);
        byte[] dataField = TLVParser.encode(tlvTransaction);
        CommandApdu command = new CommandApdu(CommandType.Get_Dynamic_Tip, p1, p2, dataField);

        ResponseMessage rm = sendAndReceive(InterfaceType.MPI, command);
        if (rm == null) {
            LOGGER.debug("Get_Dynamic_Tip null?");
            return new Result.Error<>(GetNumericDataError.InternalError);
        } else if (!rm.isSuccess()) {
            int statusCode = rm.getStatusCode();
            LOGGER.debug("Get_Dynamic_Tip failed! sw12:{}", String.format("0x%04x", statusCode));

            if (statusCode == 0x9f41) {
                return new Result.Error<>(GetNumericDataError.UserCancelled);
            } else {
                return new Result.Error<>(GetNumericDataError.InternalError);
            }
        }

        // todo match E1?
        List<TLVObject> rmE1List = TLVParser.decode(rm.getBody());
        TLVObject tlvUserNumberInput = CommandUtil.firstMatch(rmE1List, Description.Numeric_Data);
        if (tlvUserNumberInput == null) {
            return new Result.Error<>(GetNumericDataError.InternalError);
        }
        byte[] rawData = tlvUserNumberInput.getRawData();
        String strIndex = new String(rawData, US_ASCII);
        LOGGER.trace("Get_Dynamic_Tip: {}", strIndex);

        final int resultIdx;
        try {
            int resultNum = Integer.parseInt(strIndex, 10);
            resultIdx = resultNum - 1;
        } catch (NumberFormatException ex) {
            LOGGER.error("Get_Dynamic_Tip returned a non-number?");
            return new Result.Error<>(GetNumericDataError.InternalError);
        }
        if (resultIdx < 0 || resultIdx >= percentCount) {
            LOGGER.error("Get_Dynamic_Tip returned a number outside size of given list?");
            return new Result.Error<>(GetNumericDataError.InternalError);
        }
        return new Result.Success<>(resultIdx);
    }

    /**
     * Request numerical input from user.
     *
     * <p>
     * See MPI API doc '6.24 GET NUMERIC DATA' for more information on the command.
     * </p>
     *
     * <p>
     * In order to meet the security requirements for numeric entry on the PED a user can
     * only enter a non-PIN, numeric value if the contents of the display are under the
     * control of the MPI.
     * The possible text on the screen is pre-defined by a signed file, prompts.txt,
     * installed on the PED. See 14.7 Secure Prompts in the MPI API guide.
     * </p>
     *
     * <p>
     * This command is only supported on PEDs with screens.
     * </p>
     *
     * <p>
     * An 'extended mode' is available that can display a currency amount on the screen, in addition
     * to the numerical value the user is entering. Using this extended mode apps can display e.g.
     * a 'Total', but allow the user to input a smaller number to only partially pay by card.
     * </p>
     *
     * <p>
     * Note, if one of the extended mode parameters is provided:
     * {currencyCode, currencyExponent, amountInPennies, amountLine},
     * then all the other parameters must also be provided.
     * </p>
     *
     * <p>
     * The parameters inside signed file, numeric-entry.cfg, are used to format the displayed
     * amounts of of the extended amount. See '14.9 Numeric-entry.cfg' in the MPI API guide.
     * </p>
     *
     * <p>
     * The justification of the lines to display can be configured inside MPI-Dynamic.cfg file,
     * Num_Entry_Justifications section.
     * The default justification is to the left side of the display.
     * If the extended functionality is used, both the display amount and the input amount will be
     * formatted using the parameters provided inside the command's data field
     * </p>
     *
     * @param interfaceType     Which device to send it to
     * @param automaticEnter    If "enter" is automatically sent once the user types in the
     *                          maximum number of digits. e.g. if:
     *                          <pre>{@code
     *                              automaticEnter = true
     *                              numIntDigits = 1
     *                              numFracDigits = 0
     *                          }</pre>
     *
     *                          then getNumericData will complete and return the single digit
     *                          pressed by the user as soon as they press it. e.g. 2:
     *                          <pre>{@code
     *                              automaticEnter = false
     *                              numIntDigits = 1
     *                              numFracDigits = 0
     *                          }</pre>
     *                          then the user will have to press enter after typing in
     *                          a single digit.
     * @param backlightOn       If true:
     *                          Turn the LCD backlight on if it's supported by the device
     *                          If the device doesn't have a backlight, it's ignored.
     * @param firstLineIndex    The index of the text string, from prompts.txt, to use for
     *                          the first line of the display.
     *                          Value must be in the range [1, 65535]
     * @param secondLineIndex   The index of the text string, from prompts.txt, to use for
     *                          the second line of the display.
     *                          Value must be in the range [1, 65535]
     * @param thirdLineIndex    The index of the text string, from prompts.txt, to use for
     *                          the third line of the display.
     *                          Value must be in the range [1, 65535]
     * @param numIntDigits      Number of pre decimal digits (digits before the radix point)
     *                          used in the number format entered by the user.
     *                          Must be in range [0, 12]. Together with numFracDigits
     *                          the device can only display up to 12 numerical digits (not
     *                          including decimal separator and thousands separator etc)
     * @param numFracDigits     Number of post decimal digits (digits after radix point).
     *                          used in the number format entered by the user.
     *                          Must be in range [0, 11]. Together with numIntDigits
     *                          the device can only display up to 12 numerical digits (not
     *                          including decimal separator and thousands separator etc)
     * @param numberToEditAscii Optional (May by null). If non-null, it must be a string of digits
     *                          (with optional decimal separator) that will already be 'present'
     *                          in the user's edit line.
     *                          The user can backspace out these numbers or add to them
     *                          if they wish.
     * @param amountInPennies   An amount to be displayed to the user.
     *                          Optional (May by null).
     *                          If non-null, currencyExponent,
     *                          currencyCode, amountLine must also be non-null.
     * @param currencyCode      ISO 4217 currency code to be used when the amount is displayed.
     *                          Optional (May by null).
     *                          If non-null, amountInPennies must also be non-null.
     *                          See '14.9 Numeric-entry.cfg' section in the MPI API guide
     *                          for acceptable
     * @param currencyExponent  ISO 4217 currency exponent used to format the amount
     *                          If non-null, amountInPennies must also be non-null.
     * @param amountLine        Line number where the amount as label should be displayed.
     *                          Must be in the range [1,3]
     *                          If non-null, amountInPennies must also be non-null.
     * @return Either:
     * <ol>
     * <li>Success: A string (max 12 chars) containing digits and possible radix point</li>
     * <li>Error: Relevant GetNumericDataError</li>
     * </ol>
     */
    @NonNull
    public Result<String, GetNumericDataError> getNumericData(
            @NonNull InterfaceType interfaceType,
            boolean automaticEnter,
            boolean backlightOn,
            int firstLineIndex,
            int secondLineIndex,
            int thirdLineIndex,
            int numIntDigits,
            int numFracDigits,
            @Nullable String numberToEditAscii,
            @Nullable Integer amountInPennies,
            @Nullable Integer currencyCode,
            @Nullable Integer currencyExponent,
            @Nullable Integer amountLine
    ) {
        // todo this should be two overloads that exposes all of the nullables or none.
        // region parameter-validation
        if (interfaceType != InterfaceType.MPI) {
            throw new IllegalArgumentException("getNumericData only available on MPI devices");
        }

        if (firstLineIndex < 0 || firstLineIndex > 0xFFFF) {
            throw new IllegalArgumentException("firstLineIndex must be <= 0xFFFF");
        } else if (secondLineIndex < 0 || secondLineIndex > 0xFFFF) {
            throw new IllegalArgumentException("secondLineIndex must be <= 0xFFFF");
        } else if (thirdLineIndex < 0 || thirdLineIndex > 0xFFFF) {
            throw new IllegalArgumentException("thirdLineIndex must be <= 0xFFFF");
        }

        /*
            The maximum number of allowed digits is 12.
            If the number of pre decimal digits (byte 1 of tag DFA207) is bigger than 12,
            the value is truncated at 12.
            If the number of post decimal digits (byte 2 of tag DFA207) is bigger than 11, the
            "Command formatting error" (9F14) is returned.
         */

        if (numIntDigits < 0 || numIntDigits > 12) {
            String msg = String.format("numIntDigits has max of 12, not %d", numIntDigits);
            throw new IllegalArgumentException(msg);
        } else if (numFracDigits < 0 || numFracDigits > 11) {
            String msg = String.format("numFracDigits has max of 11, not %d", numFracDigits);
            throw new IllegalArgumentException(msg);
        }

        int numUserDigits = numIntDigits + numFracDigits;
        if (numUserDigits <= 0) {
            throw new IllegalArgumentException("Can't display 0 digits on display!");
        } else if (numUserDigits > 12) {
            String msg = String.format(
                    "Can't display %d digits on display! Max is 12. int: %d frac:%d",
                    numUserDigits, numIntDigits, numFracDigits
            );
            throw new IllegalArgumentException(msg);
        }

        if (numberToEditAscii != null) {

            if (!US_ASCII.newEncoder().canEncode(numberToEditAscii)) {
                throw new IllegalArgumentException("numberToEditAscii is not ascii");
            }

            /*
                The rules for decimal and thousand separator are complicated and they
                depend on the PED's config. Rather than worry about that just do some very basic
                validation now and let the PED do the rest.

                This does mean things like int=3, frac=1, edit="1234" get through, but the
                PED will reject them.
             */
            String digitsOnly = numberToEditAscii.replaceAll("[\\D]", "");
            int numAsciiDigits = digitsOnly.length();
            if (numAsciiDigits > numUserDigits) {
                String msg = String.format(""
                                + "numberToEditAscii is too long (%d digits). "
                                + "User can only enter %d (%d.%d) digits",
                        numAsciiDigits, numUserDigits, numIntDigits, numFracDigits
                );
                throw new IllegalArgumentException(msg);
            }
        }

        boolean ccProvided = currencyCode != null;
        boolean ceProvided = currencyExponent != null;
        boolean aipProvided = amountInPennies != null;
        boolean alProvided = amountLine != null;
        boolean extendedMode = ccProvided || ceProvided || aipProvided || alProvided;
        if (extendedMode) {

            boolean someMissing = !ccProvided || !ceProvided || !aipProvided || !alProvided;
            if (someMissing) {
                String msg = String.format(""
                                + "For the group {currencyCode (%s), currencyExponent (%s), "
                                + "amountInPennies (%s), amountLine (%s)}"
                                + "either ALL must be non-null or ALL must be null",
                        ccProvided ? "non-null" : "null",
                        ceProvided ? "non-null" : "null",
                        aipProvided ? "non-null" : "null",
                        alProvided ? "non-null" : "null"
                );
                throw new IllegalArgumentException(msg);
            }

            if ((amountLine <= 0) || (amountLine >= 4)) {
                throw new IllegalArgumentException("Amount line can only be: 1, 2, 3");
            }
        }

        // endregion parameter-validation

        ArrayList<TLVObject> e0List = new ArrayList<>(7);

        // Secure_Prompt is 3 16bit indices
        //noinspection NumericCastThatLosesPrecision
        TLVObject tlvSecurePrompt = new TLVObject(
                Description.Secure_Prompt,
                new byte[]{
                        (byte) ((firstLineIndex >> 8) & 0xFF),
                        (byte) (firstLineIndex & 0xFF),
                        (byte) ((secondLineIndex >> 8) & 0xFF),
                        (byte) (secondLineIndex & 0xFF),
                        (byte) ((thirdLineIndex >> 8) & 0xFF),
                        (byte) (thirdLineIndex & 0xFF),
                }
        );
        e0List.add(tlvSecurePrompt);

        TLVObject tlvNumberFormat = new TLVObject(
                Description.Number_Format,
                new byte[]{
                        intToUbyte(numIntDigits),
                        intToUbyte(numFracDigits)
                }
        );
        e0List.add(tlvNumberFormat);

        if (numberToEditAscii != null) {
            TLVObject tlvNumericData = new TLVObject(
                    Description.Numeric_Data,
                    numberToEditAscii.getBytes(US_ASCII)
            );
            e0List.add(tlvNumericData);
        }

        if (extendedMode) {
            byte[] currencyBytes = getBCD(currencyCode, 2);
            TLVObject tlvCurrencyCode = new TLVObject(
                    Description.Transaction_Currency_Code, currencyBytes
            );
            e0List.add(tlvCurrencyCode);

            TLVObject tlvCurrencyExponent = new TLVObject(
                    Description.Transaction_Currency_Exponent,
                    new byte[]{intToUbyte(currencyExponent)}
            );
            e0List.add(tlvCurrencyExponent);

            byte[] amountBytes = getBCD(amountInPennies, 6);
            TLVObject tlvAmount = new TLVObject(
                    Description.Amount_Authorised_Numeric,
                    amountBytes
            );
            e0List.add(tlvAmount);

            /*
                The valid values are 1, 2 and 3 (0x01, 0x02 and 0x03).
                Providing other values will trigger an error from MPI:
                    9F 14 - Command formatting error
            */
            TLVObject tlvAmountLine = new TLVObject(
                    Description.Amount_Line,
                    new byte[]{intToUbyte(amountLine)}
            );
            e0List.add(tlvAmountLine);
        }

        int p1 = automaticEnter ? 0x1 : 0x0;
        int p2 = backlightOn ? 0x1 : 0x0;

        TLVObject tlvTransaction = new TLVObject(Description.Command_Data, e0List);
        byte[] dataField = TLVParser.encode(tlvTransaction);
        CommandApdu command = new CommandApdu(CommandType.Get_Numeric_Data, p1, p2, dataField);

        ResponseMessage rm = sendAndReceive(interfaceType, command);
        if (rm == null) {
            LOGGER.debug("Get_Numeric_Data null?");
            return new Result.Error<>(GetNumericDataError.InternalError);
        } else if (!rm.isSuccess()) {
            int statusCode = rm.getStatusCode();
            LOGGER.debug("Get_Numeric_Data failed! sw12:{}", String.format("0x%04x", statusCode));

            if (statusCode == 0x9f41) {
                return new Result.Error<>(GetNumericDataError.UserCancelled);
            } else {
                return new Result.Error<>(GetNumericDataError.InternalError);
            }
        }

        // todo match E1?
        List<TLVObject> rmE1List = TLVParser.decode(rm.getBody());
        TLVObject tlvUserNumberInput = CommandUtil.firstMatch(rmE1List, Description.Numeric_Data);
        if (tlvUserNumberInput == null) {
            return new Result.Error<>(GetNumericDataError.InternalError);
        }
        byte[] rawData = tlvUserNumberInput.getRawData();
        return new Result.Success<>(new String(rawData, US_ASCII));
    }


    @NonNull
    static Result<byte[], TransactionResponse> processTransactionResponse(
            @Nullable ResponseMessage rm
    ) {
        if (rm == null) {
            return new Result.Error<>(TransactionResponse.UNKNOWN);
        } else if (rm.isSuccess()) {
            return new Result.Success<>(rm.getBody());
        } else {
            return new Result.Error<>(TransactionResponse.valueOf(rm.getSw2()));
        }
    }

    @NonNull
    static CommandApdu makeStartTransactionCommand(@NonNull TransactionType transactionType,
            int amountInPennies, int currencyCode) {
        byte[] amountBytes = getBCD(amountInPennies, 6);
        byte[] currencyBytes = getBCD(currencyCode, 2);

        Date date = new Date();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyMMdd");
        String strDate = dateFormat.format(date);

        SimpleDateFormat timeFormat = new SimpleDateFormat("HHmmss");
        String strTime = timeFormat.format(date);

        TLVObject tlvTransactionType = new TLVObject(
                Description.Transaction_Type, new byte[]{transactionType.getValue()}
        );
        TLVObject tlvAmount = new TLVObject(Description.Amount_Authorised_Numeric, amountBytes);
        TLVObject tlvDate = new TLVObject(Description.Date, BinaryUtil.parseHexBinary(strDate));
        TLVObject tlvTime = new TLVObject(Description.Time, BinaryUtil.parseHexBinary(strTime));
        TLVObject tlvApplicationSelection = new TLVObject(
                Description.Configure_Application_Selection,
                new byte[]{(byte) 0x01}
        );
        TLVObject tlvConfigureTRM = new TLVObject(
                Description.Configure_TRM_Stage, new byte[]{(byte) 0x00}
        );
        TLVObject tlvCurrencyCode = new TLVObject(
                Description.Transaction_Currency_Code, currencyBytes
        );

        ArrayList<TLVObject> list = new ArrayList<>();
        list.add(tlvTransactionType);
        list.add(tlvAmount);
        list.add(tlvDate);
        list.add(tlvTime);
        list.add(tlvApplicationSelection);
        list.add(tlvConfigureTRM);
        list.add(tlvCurrencyCode);

        TLVObject tlvTransaction = new TLVObject(Description.Command_Data, list);
        byte[] dataField = TLVParser.encode(tlvTransaction);

        return new CommandApdu(CommandType.Start_Transaction, dataField);
    }

    @NonNull
    static CommandApdu makeStartContactlessTransactionCommand(
            @NonNull TransactionType transactionType,
            int amountInPennies,
            int currencyCode,
            @Nullable String languagePreference
    ) {
        byte[] amountBytes = getBCD(amountInPennies, 6);
        byte[] currencyBytes = getBCD(currencyCode, 2);

        Date date = new Date();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyMMdd");
        String strDate = dateFormat.format(date);

        SimpleDateFormat timeFormat = new SimpleDateFormat("HHmmss");
        String strTime = timeFormat.format(date);

        TLVObject tlvTransactionType = new TLVObject(
                Description.Transaction_Type, new byte[]{transactionType.getValue()});
        TLVObject tlvAmount = new TLVObject(Description.Amount_Authorised_Numeric, amountBytes);
        TLVObject tlvDate = new TLVObject(Description.Date, BinaryUtil.parseHexBinary(strDate));
        TLVObject tlvTime = new TLVObject(Description.Time, BinaryUtil.parseHexBinary(strTime));
        TLVObject tlvCurrencyCode = new TLVObject(
                Description.Transaction_Currency_Code, currencyBytes);
        TLVObject tlvLanguagePreference = null;
        if (languagePreference != null) {
            if (languagePreference.length() != 2) {
                // todo bad as it's unchecked!! This won't be caught by MiuraManager?
                throw new IllegalArgumentException("Invalid ISO 3166 code");
            }
            tlvLanguagePreference = new TLVObject(
                    Description.TERMINAL_LANUAGE_PREFERENCE, languagePreference.getBytes());
        }

        ArrayList<TLVObject> list = new ArrayList<>();
        list.add(tlvTransactionType);
        list.add(tlvAmount);
        list.add(tlvDate);
        list.add(tlvTime);
        list.add(tlvCurrencyCode);
        if (tlvLanguagePreference != null) {
            list.add(tlvLanguagePreference);
        }


        TLVObject tlvTransaction = new TLVObject(Description.Command_Data, list);
        byte[] dataField = TLVParser.encode(tlvTransaction);
        return new CommandApdu(CommandType.Start_Contactless_Transaction, dataField);
    }

    /** The result of the online PIN command */
    public enum OnlinePinResultType {
        CancelOrTimeout,
        BypassedPinEntry,
        PinEnteredOk,
    }

    /**
     * Result data of the "Online PIN" command: Either an error or a 2-tuple of (Data, KSN)
     *
     * <p>
     * {@link #PinData} and {@link #PinKsn} will be non-null only if {@link #mType} is
     * {@link OnlinePinResultType#PinEnteredOk}. If it is any other value both pieces of data
     * will be null.
     * </p>
     */
    public static class OnlinePinResult {

        /** The type of result this object is. */
        @NonNull
        public final OnlinePinResultType mType;

        /**
         * Online PIN block
         * <p>
         * DUKPT Encrypted online PIN block.
         * Will be non-null only if {@link #mType} is {@link OnlinePinResultType#PinEnteredOk}
         * </p>
         */
        @Nullable
        public final byte[] PinData;

        /**
         * Online PIN Key Serial Number
         * <p>
         * Key serial number used in Online PIN block encryption
         * Will be non-null only if {@link #mType} is {@link OnlinePinResultType#PinEnteredOk}
         * </p>
         */
        @Nullable
        public final byte[] PinKsn;

        /**
         * Online PIN command failed with the given error
         *
         * @param type Online Pin result -- cannot be "OK"
         */
        private OnlinePinResult(@NonNull OnlinePinResultType type) {
            if (type == OnlinePinResultType.PinEnteredOk) {
                throw new IllegalArgumentException("PinEnteredOk should have data");
            }
            mType = type;
            PinData = null;
            PinKsn = null;
        }

        /**
         * Online PIN command success. Return data available.
         *
         * @param pinData Online PIN Key Serial Number
         * @param pinKsn  Online PIN block
         */
        private OnlinePinResult(@NonNull byte[] pinData, @NonNull byte[] pinKsn) {
            mType = OnlinePinResultType.PinEnteredOk;
            PinData = pinData;
            PinKsn = pinKsn;
        }
    }

    public enum GetNumericDataError {
        InternalError,
        UserCancelled
    }
}



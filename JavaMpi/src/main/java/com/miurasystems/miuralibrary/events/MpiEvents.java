/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.events;


import com.miurasystems.miuralibrary.enums.M012Printer;
import com.miurasystems.miuralibrary.tlv.CardData;

import java.util.concurrent.locks.ReentrantLock;

/**
 * MpiEvents is a collection of {@link MpiEventPublisher}s.
 *
 * <p>
 * It has one publisher instance for each "event" that the SDK can emit.
 * MpiEventPublisher is a simple implementation of the observable pattern.
 * An app will register listeners on the events they care about by calling
 * {@code MpiEventPublisher.register(eventListener)}.
 * Event listeners <i>can</i> be anonymous, but it's
 * not recommended to do so, otherwise they can't be deregistered.
 * </p>
 * <p>
 * Each MpiEventPublisher in the group shares a mutex. Only one publisher from
 * the group can be executing code at anyone time, meaning only one callback
 * will be active at once.
 * </p>
 * <p>
 * Each client will have a single event collection to notify events on.
 * An MpiEvents can be shared between multiple MpiClients/MiuraManager if desired
 * </p>
 * <p>
 * Events are not "queued up" in anyway. If there is no registered listener at the time
 * of event dispatch then the event is simply discarded. Put another way:
 * If a Miura device emits an event and there's no one around to hear it, then it wasn't an event.
 * </p>
 * <p>
 * Opening a session will trigger the {@link #Connected} event. Some events,
 * e.g. {@link #DeviceStatusChanged}, are activate as soon as a session begins,
 * so it's best to subscribe to these events <b>before</b> opening a session otherwise they
 * will be discarded.
 * </p>
 * <p>
 * To prevent race conditions and other problems when receiving unsolicited events from the device,
 * the SDK has a few rules in place about the use of events:
 *
 * <ul>
 * <li>
 * The SDK can call events listeners from different threads.
 * Importantly, the SDK might call an event from its InputStream reading thread.
 * <ul>
 * <li>
 * As such <b>a minimum amount of code as possible should be executed by an event listener</b>.
 * The more code executed in an event listener,
 * the less responsive the input reader thread will be.
 * </li>
 * <li>
 * Using of the synchronous interface (MpiClient) from an event listener will probably cause a
 * deadlock, as the InputStream reader will be waiting to receive a message from a queue
 * that it should have posted it.
 * </li>
 * <li> Use of the asynchronous interface (MiuraManager) from a listener is fine</li>
 * </ul>
 * </li>
 * <li> There can be only one registered listener for each event at a time.</li>
 * <li> The SDK will only ever call one event listener in an MpiEvents collection at a time.
 * <ul>
 * <li> It will never call them concurrently. </li>
 * <li> An event shouldn't call notify from a listener. (Whilst an app does not have
 * the correct visibility to call notify, it can end up doing so by calling a method on
 * MpiEvents that results in e.g. a Disconnect.)
 * </li>
 * </ul>
 * </li>
 * <li> As the SDK will only call one event at once it's possible for other events to be "waiting"
 * </li>
 * <li> A listener may register/de-register itself and other listeners without any problems.
 * <ul>
 * <li>
 * This has a <i>happens-before</i> relationship with the next event to be called.
 * (In general register/deregister has a <i>happens-before</i> relationship to event calling,
 * whether those methods are called from a listener or normal code).
 * </li>
 * </ul>
 * </li>
 * <li> A listener <i>might</i> be called once deregistered.</li>
 * <li> Exceptions will not be caught</li>
 * </ul>
 * </p>
 *
 * <p>
 * This may seem like a lot of rules, but the general rule is "keep event listeners small"
 * and "don't directly call MpiEvents methods from a listener".
 * </p>
 * <p>
 * As the event listeners are expected to contain a small amount of code it is expected that apps
 * have a system in place to handle these events properly and schedule any large work they wish to
 * perform after an event happens on the appropriate worker thread.
 * </p>
 */
public class MpiEvents {

    /**
     * Connected event.
     *
     * <p>
     * Notified whenever a session is successfully opened to the device.
     * For both PEDs or POS (it is for the Connector session).
     * <b>So ensure that this is registered before trying to open a session.</b>
     * </p>
     */
    public final MpiEventPublisher<ConnectionInfo> Connected;

    /**
     * Disconnected event.
     *
     * <p>
     * Notified whenever a session to the device is closed.
     * For both PEDs or POS (it is for the Connector session).
     * </p>
     */
    public final MpiEventPublisher<ConnectionInfo> Disconnected;


    /**
     * CardStatusChanged unsolicited event.
     *
     * <p> This is for PED devices. See MPI API doc 7.1 </p>
     *
     * <p>
     * This message is used to inform the integrating application that the PED has either received
     * data from a magnetic card swipe,
     * or the user has moved a card in or out of the smartcard reader.
     * </p>
     *
     * <p>Controlled by MiuraManager.cardStatus(X)</p>
     */
    public final MpiEventPublisher<CardData> CardStatusChanged;

    /**
     * Key Press unsolicited event.
     *
     * <p> This is for PED devices. See MPI API doc 7.2 </p>
     *
     * <p>
     * Once activated, this message is sent whenever the user presses a key on the PED keypad. The
     * response message will contain information on which key was pressed. All numeric keys will
     * return the code '00' as these are masked due to security restrictions.
     * </p>
     *
     * <p>
     * Controlled by MiuraManager.keyboardStatus(StatusSettings.X, ...).
     * </p>
     */
    public final MpiEventPublisher<Integer> KeyPressed;


    /**
     * Device Status Change unsolicited event.
     *
     * <p> This is for PED devices. See MPI API doc 7.3 </p>
     *
     * <p>
     * This message will be sent each time the PED's status changes, such as powered on,
     * or entered a wait state e.g. PIN Entry.
     * This message is intended to allow the device to inform the integrating
     * application of any important system wide change.
     * </p>
     *
     * <p>
     * <b>This message is always active and cannot be disabled</b>
     * <b>So ensure that this is registered before trying to open a session</b>, otherwise
     * you will miss "device powered on".
     * </p>
     */
    public final MpiEventPublisher<DeviceStatusChange> DeviceStatusChanged;


    /**
     * PRINTER STATUS CHANGE unsolicited event.
     *
     * <p> This is for PED devices. See MPI API doc 7.5 </p>
     * <p>
     * This message is sent to inform the integrating application that the printer status has
     * changed. From the printer status message it can be inferred if printing has been scheduled,
     * if printing has completed, printer paper ok/out, if any error occurred while printing.
     * </p>
     *
     * <p>
     * Controlled by MiuraManager.printerSledStatus(X, ...).
     * </p>
     */
    public final MpiEventPublisher<M012Printer> PrinterStatusChanged;

    /**
     * COMMUNICATION CHANNEL STATUS unsolicited event. <b>not currently used in SDK</b>
     * <p> This is for PED devices. See MPI API doc 7.6 </p>
     */
    @Deprecated
    public final MpiEventPublisher<CommsStatusChange> CommsChannelStatusChanged;


    /**
     * Bar Code Data unsolicited event.
     *
     * <p>This is for POS devices. See RPI API doc 7.1 </p>
     *
     * <p>
     * This message is used to inform the integrating application that bar code data is available.
     * The message data is ASCII.
     * </p>
     *
     * <p>
     * Controlled by MiuraManager.barcodeScannerStatus(X, ...).
     * </p>
     */
    public final MpiEventPublisher<String> BarcodeScanned;

    /**
     * USB SERIAL unsolicited event.
     *
     * <p>This is for POS devices. See RPI API doc 7.2 </p>
     *
     * <p>
     * This message is used to inform the integrating application that data from USB Serial port
     * is available. This message will be received if an USB Serial adapter has been plugged in.
     * </p>
     *
     * <p>
     * <b>This message is always active and cannot be disabled</b>
     * <b>So ensure that this is registered before trying to open a session</b>, otherwise
     * you will miss an initial message.
     * </p>
     */
    public final MpiEventPublisher<byte[]> UsbSerialPortDataReceived;

    /**
     * Create a new MpiEvents collection
     */
    public MpiEvents() {

        ReentrantLock groupLock = new ReentrantLock(true);

        Connected = new MpiEventPublisher<>(groupLock);
        Disconnected = new MpiEventPublisher<>(groupLock);
        CardStatusChanged = new MpiEventPublisher<>(groupLock);
        KeyPressed = new MpiEventPublisher<>(groupLock);
        DeviceStatusChanged = new MpiEventPublisher<>(groupLock);
        PrinterStatusChanged = new MpiEventPublisher<>(groupLock);
        CommsChannelStatusChanged = new MpiEventPublisher<>(groupLock);
        BarcodeScanned = new MpiEventPublisher<>(groupLock);
        UsbSerialPortDataReceived = new MpiEventPublisher<>(groupLock);
    }
}

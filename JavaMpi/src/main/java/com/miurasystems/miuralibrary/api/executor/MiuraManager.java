/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.api.executor;


import static com.miurasystems.miuralibrary.MpiClient.GetNumericDataError;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.miurasystems.miuralibrary.MpiClient;
import com.miurasystems.miuralibrary.MpiClientMTAbort;
import com.miurasystems.miuralibrary.Result;
import com.miurasystems.miuralibrary.api.listener.APITransferFileListener;
import com.miurasystems.miuralibrary.api.listener.ApiBatteryStatusListener;
import com.miurasystems.miuralibrary.api.listener.ApiBlueToothInfoListener;
import com.miurasystems.miuralibrary.api.listener.ApiCashDrawerListener;
import com.miurasystems.miuralibrary.api.listener.ApiContinueTransactionListener;
import com.miurasystems.miuralibrary.api.listener.ApiGetConfigListener;
import com.miurasystems.miuralibrary.api.listener.ApiGetDeviceFileListener;
import com.miurasystems.miuralibrary.api.listener.ApiGetDeviceInfoListener;
import com.miurasystems.miuralibrary.api.listener.ApiGetNumericDataListener;
import com.miurasystems.miuralibrary.api.listener.ApiGetSoftwareInfoListener;
import com.miurasystems.miuralibrary.api.listener.ApiGetSystemClockListener;
import com.miurasystems.miuralibrary.api.listener.ApiOnlinePinListener;
import com.miurasystems.miuralibrary.api.listener.ApiP2PEImportListener;
import com.miurasystems.miuralibrary.api.listener.ApiP2PEStatusListener;
import com.miurasystems.miuralibrary.api.listener.ApiPeripheralTypeListener;
import com.miurasystems.miuralibrary.api.listener.ApiStartTransactionListener;
import com.miurasystems.miuralibrary.api.listener.MiuraDefaultListener;
import com.miurasystems.miuralibrary.api.objects.BatteryData;
import com.miurasystems.miuralibrary.api.objects.Capability;
import com.miurasystems.miuralibrary.api.objects.P2PEStatus;
import com.miurasystems.miuralibrary.api.objects.SoftwareInfo;
import com.miurasystems.miuralibrary.api.utils.GetDeviceFile;
import com.miurasystems.miuralibrary.api.utils.SerialPortProperties;
import com.miurasystems.miuralibrary.api.utils.StreamBinaryFile;
import com.miurasystems.miuralibrary.comms.Connector;
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
import com.miurasystems.miuralibrary.events.MpiEvents;
import com.miurasystems.miuralibrary.tlv.BinaryUtil;
import com.miurasystems.miuralibrary.tlv.Description;
import com.miurasystems.miuralibrary.tlv.TLVObject;
import com.miurasystems.miuralibrary.tlv.Track2Data;
import com.miurasystems.miuralibrary.update.FileUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The primary Miura SDK class apps should use to interact with a Miura devices.
 *
 * <p>
 * MiuraManager offers a Java-level API for interacting with Miura devices over a
 * {@link Connector}.
 * </p>
 *
 * <p>
 * MiuraManager deals in connection "sessions". A single session is all the command/response pairs
 * sent between {@link #openSession()} and {@link #closeSession()}
 * (or more accurately, between a {@link MpiEvents#Connected} event and
 * a {@link MpiEvents#Disconnected} event, as the app might not call {@code closeSession()} but the
 * connection still might break).
 * These sessions match a Miura devices' notion of state persistence for various commands.
 * e.g. if the connection breaks then a Miura PED will abort current transactions and reset
 * its display back to the idle state. MiuraManager starts with no active session
 * and <b>a new session must be {@link #openSession() opened}
 * before a command can be sent or an unsolicited message received</b>.
 * Only one session can be open at a time.
 * And after a session closes MiuraManager can no longer send commands or
 * receive unsolicited messages until a new session is opened.
 * </p>
 *
 * <p>
 * MiuraManager is a singleton and is intended to live as long as your app does.
 * </p>
 *
 * <p>
 * All MiuraManager methods are asynchronous and will launch tasks in a separate, dedicated
 * thread. <b>The exception to this is {@link #openSession()} and {@link #closeSession()}</b>.
 * </p>
 *
 * <p>
 * MiuraManager only uses a single thread to run all its command-sending methods on, and it
 * will run them in the order the methods were called.
 * It will only send command N after command N-1 has been received.
 * The {@link #abortTransaction(MiuraDefaultListener)} command will use a second thread to
 * ensure it can interrupt a blocked {@link #startTransaction}.
 * </p>
 *
 * <p>
 * The results of the asynchronous operations are returned via <i>listeners</i>. All of the
 * listeners follow the same basic model: If the call worked, onSuccess is called. If they failed,
 * onError is called. See the classes in the package
 * {@link com.miurasystems.miuralibrary.api.listener} for more.
 * </p>
 *
 * <p>
 * MiuraManager will invoke all listener callbacks <b>from threads that the SDK has created</b>.
 * The listeners provided by the command methods will be running on the async thread and events
 * can be running on "any" thread. The integrating app should take the appropriate synchronisation
 * precautions. See 'Internal use of threads and events' and 'Thread safety' below.
 * </p>
 *
 * <p>
 * MiuraManager interacts with single connection at a time, represented by a {@link Connector},
 * and on the other end of that connection there may be multiple Miura devices responding to
 * different {@link DeviceType}s. (It could be just a single device, e.g. a Miura PED,
 * but it may be e.g. a Miura POSzle with a Miura PED attached as a peripheral).
 * To specify which device at the other end of the connection MiuraManager is communicating with
 * use {@link #setDeviceType(DeviceType)}.
 * </p>
 *
 * <p>
 * The devices on the other end of a connection may wish to send messages to the app.
 * MiuraManager will notify the app when these messages arrive by signalling the appropriate event
 * on its {@link MpiEvents} instance. MiuraManager will also signal
 * {@link MpiEvents#Connected Connected} & {@link MpiEvents#Disconnected Disconnected}
 * events as they happen using the same MpiEvents object.
 * MiuraManager's event object can be retrieved via {@link #getMpiEvents()}.
 * </p>
 *
 * <h4>Internal use of threads and events</h4>
 *
 * <p>
 * Due to the asynchronous nature of unsolicited messages MiuraManager uses a separate thread
 * to read the Connector's InputStream.
 * MiuraManager spawns this new thread each time openSession() is called.
 * The Event notification callback <b>can happen from either the InputStream reader thread or
 * MiuraManager's async thread</b>. Event handlers need to be aware of this.
 * Command listeners will be invoked by MiuraManager's async thread.
 * </p>
 *
 * <p>
 * An important consequence of this is that
 * <b>a minimum amount of code as possible should be put in an event listener</b>.
 * The more code that is in an event listener, the less responsive the input reader thread will be.
 * See {@link MpiEvents} for more information about the threading model.
 * </p>
 * <p>
 * Note that it's fine to call MiuraManager methods from the callback thread as these will
 * be scheduled for execution sometime after this listener returns.
 * This includes abortTransaction. MiuraManager ensures it is scheduled on thread
 * that can interrupt a stalled transaction.
 * </p>
 * <p>
 * Though note that any previously unexecuted MiuraManager commands will execute before the ones
 * scheduled by the current listener, but that situation
 * shouldn't arise as having outstanding commands implies either
 * <ol>
 * <li>concurrent use of MiuraManager</li>
 * <li>calling MiuraManager methods in a procedural style</li>
 * </ol>
 * both of which are bad and a source of bugs.
 * </p>
 *
 * <h4>Thread safety</h4>
 * <p>
 * With the except of calling {@link #abortTransaction}, it is not recommended to use MiuraManager
 * in a <b>concurrent</b> manner.
 * </p>
 *
 * <p>
 * MiuraManagers underlying use of MpiClientMTAbort <b>is</b> thread safe.
 * However some parts of it, e.g. changing device type, <b>aren't</b> thread safe.
 * </p>
 *
 * <p>
 * But, other than doing a mid-transaction abort, there's no need for it to <i>be</i> thread safe as
 * it doesn't make a great deal of sense to call methods on MiuraManager
 * from multiple threads without the app already doing some kind of synchronisation.
 * The thread safety is only there to stop it being incredibly tedious for an app to send an
 * abort without it worrying about the inherent race condition of "has the transaction finished
 * and moved onto the displayText yet?"
 * </p>
 *
 * <p>
 * (The reason for that is that the class is an abstractions over a single communication channel.
 * If the app wishes to send commands concurrently from multiple threads
 * then that channel needs to be synchronised by the App, otherwise the order of commands
 * seen by the device will be chaotic. There would be no way for the thread-safe MiuraManager to
 * know which of the two concurrently sending commands the app wishes
 * to execute first on the device.)
 * </p>
 */
public class MiuraManager {

    /** SLF4J Logger */
    private static final Logger LOGGER = LoggerFactory.getLogger(MiuraManager.class);

    /** Creates threads for {@link #mExecutor} */
    @NonNull
    private static final ThreadFactory ASYNC_THREAD_FACTORY =
            new MiuraManagerThreadFactory("AsyncThread");

    /** Creates threads for {@link #mAbortExecutor} */
    @NonNull
    private static final ThreadFactory ABORT_THREAD_FACTORY =
            new MiuraManagerThreadFactory("AbortThread");

    /** MiuraManager's singleton instance. */
    @Nullable
    private static MiuraManager sInstance = null;

    /** MpiEvents object to post events to. */
    @NonNull
    private final MpiEvents mMpiEvents;

    /**
     * The MpiClient this MiuraManager wraps and calls asynchronously
     * <p>
     * Will be null if a session is not opened yet. See {@link #openSession()}
     * </p>
     */
    @Nullable
    private MpiClient mMpiClient;

    /** The device type MiuraManager is currently sending on. */
    @NonNull
    private DeviceType mDeviceType;

    /**
     * The executor to run the async tasks on.
     *
     * <p> Will be null until {@link #openSession()} is called. And will point to a different
     * executor each time a new session is opened.
     * </p>
     */
    @Nullable
    private ExecutorService mExecutor;

    /**
     * The executor to run the abort tasks on.
     * <p> Will be null until {@link #openSession()} is called. And will point to a different
     * executor each time a new session is opened.
     * </p>
     */
    @Nullable
    private ExecutorService mAbortExecutor;

    @Nullable
    private Connector mConnector;

    private MiuraManager() {
        mDeviceType = DeviceType.PED;
        mMpiEvents = new MpiEvents();
        mMpiClient = null;
        mExecutor = null;
        mAbortExecutor = null;
    }

    /**
     * Get the connector currently used by MiuraManager
     *
     * @return Return the current connector.
     */
    @Nullable
    public Connector getConnector() {
        return mConnector;
    }

    /**
     * Set the connector to use with MiuraManager.
     *
     * <p>
     * This creates a new MpiClient that uses the Connector to talk to the Miura device
     * on the other end of the Connector.
     * </p>
     *
     * <p>
     * If setConnector has yet to be called then there
     * will be now current client, no open session, and therefore no commands can be sent.
     * Once setConnector has been called a session should be opened via {@link #openSession()}.
     * </p>
     *
     * <p>
     * <b>Note: This method is not asynchronous.</b> It calls {@link #closeSession()},
     * which may block the calling thread.
     * </p>
     *
     * @param connector Uses the given Connector to interact with the Miura device at the other
     *                  end. MiuraManager now <b>exclusively</b> "owns" this Connector
     *                  (and therefore the underling data link).
     *                  <b>Do not</b> interact with the Connector or data-link after
     *                  calling setConnector(connector).
     */
    public void setConnector(@NonNull Connector connector) {

        closeSession();

        // noinspection VariableNotUsedInsideIf
        mConnector = connector;
        mMpiClient = new MpiClientMTAbort(mConnector, mMpiEvents);
    }

    /**
     * Opens a new session on MiuraManager's current Connector.
     *
     * <p>
     * Uses the Connector given by {@link #setConnector(Connector)} to connect to the Miura
     * device and open a session. If the session is opened without error then a
     * {@link MpiEvents#Connected Connected} should occur.
     * </p>
     * <p>
     * Once a session is open commands may be sent. Until a session is open, commands may
     * not be sent.
     * </p>
     * <p>
     * If the Connector is not a usb-serial data link, then after the
     * {@link MpiEvents#Connected Connected} event there
     * should also be a {@link MpiEvents#DeviceStatusChanged DeviceStatusChanged}
     * event from the Miura device.
     * </p>
     *
     * <p>
     * <b>Note: This method is not asynchronous and may block the calling thread.</b>
     * </p>
     *
     * @throws IOException If there was a problem opening the session.
     */
    public void openSession() throws IOException {

        LOGGER.trace("openSession()");

        if (mMpiClient == null || mConnector == null) {
            throw new IOException("No connector set?");
        }
        closeSession();

        //noinspection VariableNotUsedInsideIf
        if (mExecutor != null) throw new IOException("mExecutor != null? ");
        if (mAbortExecutor != null) throw new IOException("mAbortExecutor != null? ");

        mExecutor = Executors.newSingleThreadExecutor(ASYNC_THREAD_FACTORY);
        mAbortExecutor = Executors.newSingleThreadExecutor(ABORT_THREAD_FACTORY);
        mMpiClient.openSession();
    }

    /**
     * Closes a session on the Connector.
     *
     * <p>Will cause a {@link MpiEvents#Disconnected Disconnected} event.</p>
     *
     * <b>Note: This method is not asynchronous and may block the calling thread.</b>
     */
    public void closeSession() {
        LOGGER.trace("closeSession()");

        /* If something on the Async thread calls closeSession then we don't want to do
         * shutdownNow, as that will just result in interruptedException whilst executing
         * mMpiClient.closeSession().
         *
         * So do shutdownNow after mMpiClient.closeSession().
         */
        if (mExecutor != null) {
            /* Stop any new tasks from being added */
            mExecutor.shutdown();
        }
        if (mAbortExecutor != null) {
            /* Stop any new tasks from being added */
            mAbortExecutor.shutdown();
        }

        if (mMpiClient != null) {
            //if (mMpiClient.isOpen()) {
            mMpiClient.closeSession();
            //}
        }

        if (mExecutor != null) {
            /* To clear any pending tasks is shutdown/now, both of which break
             * the executor and mark it as "terminated", so drop the reference to it.
             * (If we wanted to keep the same executor we'd need a bunch of logic to track our
             *  own list of tasks etc)
             */
            mExecutor.shutdownNow();
            mExecutor = null;
        }
        if (mAbortExecutor != null) {
            mAbortExecutor.shutdownNow();
            mAbortExecutor = null;
        }
    }

    /**
     * @deprecated Timeouts should be used in the Connector
     */
    @Deprecated
    public void setTimeout(long timeout) {
    }

    /**
     * Get a reference to MiuraManager's current MpiEvents object.
     *
     * @return the MpiEvent object.
     */
    @NonNull
    public MpiEvents getMpiEvents() {

        if (mMpiClient != null) {
            //noinspection ObjectEquality
            if (mMpiEvents != mMpiClient.getMpiEvents()) throw new AssertionError();
        }

        return mMpiEvents;
    }

    /**
     * Get the currently wrapped MpiClient
     *
     * <p>
     * Note: This is a rather dangerous call. Only use it if you know what you're doing!
     * The lifetime of the object returned by this method is ended when the next call to
     * {@link #setConnector(Connector)} happens.
     * </p>
     *
     *
     * <ol>
     *     <li>
     *     If {@link #setConnector(Connector)} has yet to be called, the return value will be
     *     null.
     *     </li>
     *     <li>
     *         Whenever {@link #setConnector(Connector)} is called the old values returned by this
     *         function will "expire" and no longer be valid MpiClients for this MiuraManager
     *     </li>
     * </ol>
     *
     * @return an mpi client if one is available
     */
    @Nullable
    public MpiClient getMpiClient() {
        return mMpiClient;
    }

    /**
     * Get MiuraManager's current device type.
     *
     * @return Selected {@link DeviceType}
     */
    @NonNull
    public DeviceType getDeviceType() {
        return mDeviceType;
    }

    /**
     * Change which device type MiuraManager will communicate with.
     *
     * <p>
     * The other end of the Connector can be talking to multiple Miura Devices, e.g. both a POS
     * and a PED. The device type specifies which device to send commands to.
     * </p>
     *
     * @param deviceType {@link DeviceType}
     */
    public void setDeviceType(@NonNull DeviceType deviceType) {
        this.mDeviceType = deviceType;
    }

    /**
     * @return InterfaceType Selected type device to communication.
     */
    @NonNull
    private InterfaceType getInterfaceType() {
        switch (mDeviceType) {
            case PED:
                return InterfaceType.MPI;
            case POS:
                return InterfaceType.RPI;
            default:
                return InterfaceType.MPI;
        }
    }


    /**
     * Issues a BATTERY_STATUS command to the Miura Device.
     *
     * <p>
     * See MPI API doc '6.14 BATTERY STATUS' for more information on the command.
     * </p>
     *
     * <p>
     * This command returns information about the device's battery:
     * <ul>
     * <li>batteryLevel, in percentage</li>
     * <li>{@link ChargingStatus}, cast to an int. Information about battery state</li>
     * </ul>
     * </p>
     *
     * <p>
     * The command/response will be sent/received asynchronously. This call will not
     * block the calling thread.
     * </p>
     *
     * @param listener Listener to call with results.
     */
    public void getBatteryStatus(@NonNull final ApiBatteryStatusListener listener) {
        executeAsync(new AsyncRunnable() {
            @Override
            public void runOnAsyncThread(@NonNull MpiClient client) {
                BatteryData batteryData = client.batteryStatus(getInterfaceType(), false);

                if (batteryData != null) {
                    ChargingStatus chargingStatus = batteryData.mChargingStatus;
                    int chargingStatusInt = BinaryUtil.ubyteToInt(chargingStatus.getValue());
                    listener.onSuccess(chargingStatusInt, batteryData.mBatteryLevel);
                } else {
                    listener.onError();
                }
            }
        });

    }

    /**
     * Issues a RESET_DEVICE command to the Miura Device to get device information.
     *
     * <p>
     * See MPI API doc '6.1 RESET DEVICE' for more information on the command.
     * </p>
     *
     * <p>
     * The soft reset issued to the device will also end the processing of any current commands
     * and wait-states, and will reset internal state variables.
     * This command returns information about the device via {@link SoftwareInfo}.
     * </p>
     *
     * <p>
     * The command/response will be sent/received asynchronously. This call will not
     * block the calling thread.
     * </p>
     *
     * @param listener Listener to call with results.
     */
    public void getSoftwareInfo(@NonNull final ApiGetSoftwareInfoListener listener) {
        executeAsync(new AsyncRunnable() {
            @Override
            public void runOnAsyncThread(@NonNull MpiClient client) {
                SoftwareInfo softwareInfo =
                        client.resetDevice(getInterfaceType(), ResetDeviceType.Soft_Reset);
                if (softwareInfo != null) {
                    listener.onSuccess(softwareInfo);
                } else {
                    listener.onError();
                }
            }
        });
    }

    /**
     * Returns a list of configuration files on the device, along with their versions.
     *
     * @param listener {@link ApiGetConfigListener} Event listener for result with configuration
     *                 files
     */
    public void getPEDConfig(@NonNull final ApiGetConfigListener listener) {
        executeAsync(new AsyncRunnable() {
            @Override
            public void runOnAsyncThread(@NonNull MpiClient client) {
                HashMap<String, String> versionMap = client.getConfiguration();
                if (versionMap != null) {
                    listener.onSuccess(versionMap);
                } else {
                    listener.onError();
                }
            }
        });
    }

    /**
     * @param text     Text to display
     * @param listener Called on finish, return result of performed action
     */
    public void displayText(
            @NonNull final String text,
            @Nullable final MiuraDefaultListener listener
    ) {
        executeAsyncDefaultListener(listener, new AsyncBooleanRunnable() {
            @Override
            public boolean runOnAsyncThread(@NonNull MpiClient client) {
                return client.displayText(getInterfaceType(), text, true, true, true);
            }
        });
    }

    /**
     * Sets keyboard-related flags.
     *
     * <p>
     * Enables/disables {@link MpiEvents#KeyPressed} events.
     * </p>
     *
     * @param statusSettings    Use it to enable/disable sending keyboard events
     * @param backlightSettings Use it to enable/disable keyboard backlight (on selected devices)
     * @param listener          {@link MiuraDefaultListener} Event listener for result
     */
    public void keyboardStatus(
            @NonNull final StatusSettings statusSettings,
            @NonNull final BacklightSettings backlightSettings,
            @Nullable final MiuraDefaultListener listener
    ) {
        executeAsyncDefaultListener(listener, new AsyncBooleanRunnable() {
            @Override
            public boolean runOnAsyncThread(@NonNull MpiClient client) {
                return client.keyboardStatus(
                        getInterfaceType(), statusSettings, backlightSettings);
            }
        });
    }


    /**
     * This allows the download of Binary files from the PED.
     *
     * @param listener {@link ApiGetDeviceFileListener} Event listener for downloaded file byte
     *                 content
     */
    public void downloadBinaryWithFileName(
            @NonNull final String fileName,
            @NonNull final ApiGetDeviceFileListener listener
    ) {
        executeAsync(new AsyncRunnable() {
            @Override
            public void runOnAsyncThread(@NonNull MpiClient client) {
                byte[] deviceFileBytes = GetDeviceFile.getDeviceFile(
                        client, getInterfaceType(), fileName,
                        new GetDeviceFile.ProgressCallback() {
                            @Override
                            public void onProgress(float fraction) {
                                listener.onProgress(fraction);
                            }
                        });
                if (deviceFileBytes != null) {
                    listener.onSuccess(deviceFileBytes);
                } else {
                    listener.onError();
                }
            }
        });
    }


    /**
     * Method to transfer a file or input stream to the connected device.
     * The caller needs to specify the name of the file which the device will interpret.
     *
     * Transfers the files in small chunks to avoid consuming so much memory and calls the
     * progress callback at the appropriate time
     *
     * @param fileName   File name for the file to be sent to the Device.
     * @param fileStream Input stream for the file to send
     * @param listener   {@link MiuraDefaultListener} Listener to indicate transfer complete or an
     *                   error.
     */
    public void transferFileToDevice(
            @NonNull final String fileName,
            @NonNull final InputStream fileStream,
            @NonNull final APITransferFileListener listener
    ) {
        executeAsync(new AsyncRunnable() {
            @Override
            public void runOnAsyncThread(@NonNull MpiClient client) {
                boolean ok = StreamBinaryFile.streamBinaryFile(
                        client,
                        getInterfaceType(), fileName, fileStream,
                        new StreamBinaryFile.ProgressCallback() {
                            @Override
                            public void onProgress(int bytesTransferred) {
                                listener.onProgress(bytesTransferred);
                            }
                        });
                if (ok) {
                    listener.onSuccess();
                } else {
                    listener.onError();
                }
            }
        });
    }

    /**
     * Method for upload binary to device.
     * <p>
     * Two steps method: First selecting file and second upload it.
     * </p>
     *
     * @param file     @link File} Selected file
     * @param listener {@link MiuraDefaultListener} Listener for action result
     */
    public void uploadBinary(
            @NonNull File file,
            @Nullable final MiuraDefaultListener listener
    ) {
        byte[] data = FileUtil.fileToByte(file.getName(), 0, (int) file.length());
        uploadBinary(data, file.getName(), listener);
    }

    /**
     * Method for upload binary to device.
     * <p>
     * Two steps method: First selecting file and second upload it.
     * </p>
     *
     * @param data     file raw data
     * @param fileName selected file name
     * @param listener {@link MiuraDefaultListener} Listener for action result
     */
    public void uploadBinary(
            @NonNull final byte[] data,
            @NonNull final String fileName,
            @Nullable final MiuraDefaultListener listener
    ) {
        executeAsyncDefaultListener(listener, new AsyncBooleanRunnable() {
            @Override
            public boolean runOnAsyncThread(@NonNull MpiClient client) {
                int pedFileSize = client.selectFile(
                        getInterfaceType(), SelectFileMode.Truncate, fileName);
                //noinspection SimplifiableIfStatement
                if (pedFileSize < 0) {
                    return false;
                }
                return client.streamBinary(
                        getInterfaceType(), false, data, 0, data.length, 100);
            }
        });
    }

    /**
     * Hard reset device. Used for update software on device
     *
     * @param listener {@link MiuraDefaultListener} Result listener
     */
    public void hardReset(@Nullable final MiuraDefaultListener listener) {
        executeAsyncDefaultListener(listener, new AsyncBooleanRunnable() {
            @Override
            public boolean runOnAsyncThread(@NonNull MpiClient client) {
                SoftwareInfo softwareInfo = client.resetDevice(getInterfaceType(),
                        ResetDeviceType.Hard_Reset);
                return softwareInfo != null;
            }
        });
    }

    /**
     * Clear any previously transferred files from the device.
     * <p>
     * This method should be used prior to sending and updates to the PED
     * to make sure there are no unexpected files on the device from a
     * previously failed transfer.
     * </p>
     *
     * @param listener {@link MiuraDefaultListener} Result listener
     */
    public void clearDeviceMemory(@Nullable final MiuraDefaultListener listener) {
        executeAsyncDefaultListener(listener, new AsyncBooleanRunnable() {
            @Override
            public boolean runOnAsyncThread(@NonNull MpiClient client) {
                SoftwareInfo softwareInfo = client.resetDevice(getInterfaceType(),
                        ResetDeviceType.Clear_Files);
                return softwareInfo != null;
            }
        });
    }

    /**
     * Returns current date on connected Miura device.
     *
     * @param listener {@link ApiGetSystemClockListener} Event listener for result with Device date
     */
    public void getSystemClock(@NonNull final ApiGetSystemClockListener listener) {
        executeAsync(new AsyncRunnable() {
            @Override
            public void runOnAsyncThread(@NonNull MpiClient client) {
                Date dateTime = client.systemClock(getInterfaceType());
                if (dateTime != null) {
                    listener.onSuccess(dateTime);
                } else {
                    listener.onError();
                }
            }
        });
    }

    /**
     * Downloads system log data
     *
     * @param listener {@link ApiGetDeviceFileListener} Event listener for downloaded file byte
     *                 content
     */
    public void getSystemLog(@NonNull final ApiGetDeviceFileListener listener) {
        executeAsync(new AsyncRunnable() {
            @Override
            public void runOnAsyncThread(@NonNull MpiClient client) {
                boolean success = client.systemLog(getInterfaceType(),
                        SystemLogMode.Archive);

                if (success) {
                    String fileName =
                            (getInterfaceType() == InterfaceType.MPI) ? "mpi.log" : "rpi.log";

                    byte[] deviceFileBytes = GetDeviceFile.getDeviceFile(
                            client, getInterfaceType(), fileName,
                            new GetDeviceFile.ProgressCallback() {
                                @Override
                                public void onProgress(float fraction) {
                                    listener.onProgress(fraction);
                                }
                            });
                    if (deviceFileBytes != null) {
                        success = client.systemLog(getInterfaceType(), SystemLogMode.Remove);
                        if (!success) {
                            LOGGER.debug("Failed to remove system log after retrieve it?");
                        }
                        listener.onSuccess(deviceFileBytes);
                        return;
                    }
                }
                listener.onError();
            }
        });
    }

    /**
     * Removes system log on device
     *
     * @param listener {@link MiuraDefaultListener} Event listener result
     */
    public void deleteLog(@Nullable final MiuraDefaultListener listener) {
        executeAsyncDefaultListener(listener, new AsyncBooleanRunnable() {
            @Override
            public boolean runOnAsyncThread(@NonNull MpiClient client) {
                return client.systemLog(getInterfaceType(), SystemLogMode.Remove);
            }
        });
    }

    /**
     * Returns list of {@link Capability} of the device
     *
     * @param listener {@link ApiGetDeviceInfoListener} Event listener for result
     */
    public void getDeviceInfo(@NonNull final ApiGetDeviceInfoListener listener) {
        executeAsync(new AsyncRunnable() {
            @Override
            public void runOnAsyncThread(@NonNull MpiClient client) {
                ArrayList<Capability> capabilities = client.getDeviceInfo(getInterfaceType());
                if (capabilities != null && capabilities.size() > 0) {
                    listener.onSuccess(capabilities);
                } else {
                    listener.onError();
                }
            }
        });
    }

    /**
     * Updates device's date using the given date.
     * <p>
     * Note: The date should be provided in iOS system Time Zone.
     * </p>
     *
     * @param newDate  {@link Date} Date to set, usually basic new Date()
     * @param listener {@link MiuraDefaultListener} Event listener for result
     */
    public void setSystemClock(
            @NonNull final Date newDate,
            @Nullable final MiuraDefaultListener listener
    ) {
        executeAsyncDefaultListener(listener, new AsyncBooleanRunnable() {
            @Override
            public boolean runOnAsyncThread(@NonNull MpiClient client) {
                return client.systemClock(getInterfaceType(), newDate);
            }
        });
    }

    /**
     * Enable or disable card receiving of card status events, used in payment
     *
     * <p>
     * Enables/disables {@link MpiEvents#CardStatusChanged} events.
     * </p>
     *
     * @param enableCardStatusChange status - enable or disable
     */
    public void cardStatus(final boolean enableCardStatusChange) {
        executeAsync(new AsyncRunnable() {
            @Override
            public void runOnAsyncThread(@NonNull MpiClient client) {
                // cardStatus returns nothing! It 'returns' an unsolicited message.
                client.cardStatus(getInterfaceType(),
                        enableCardStatusChange, false, true, true, true);
            }
        });
    }

    /**
     * Initiates Online PIN process.
     * <p>
     * If successful, response will contain at least these fields:
     * - Online PIN block
     * - Online PIN Key Serial Number
     *
     * @param amountInPennies  Amount in pennies
     * @param currencyCode     ISO 4217 code, see here for details: https://en.wikipedia
     *                         .org/wiki/ISO_4217
     * @param maskedTrack2Data The masked track 2 data
     * @param applicationLabel he text to be displayed during PIN entry
     * @param listener         {@link ApiOnlinePinListener} Event listener for result with online
     *                         PIN block and KSN
     */
    public void onlinePin(
            final int amountInPennies,
            final int currencyCode,
            @NonNull final Track2Data maskedTrack2Data,
            @NonNull final String applicationLabel,
            @NonNull final ApiOnlinePinListener listener
    ) {
        executeAsync(new AsyncRunnable() {
            @Override
            public void runOnAsyncThread(@NonNull MpiClient client) {
                Result<MpiClient.OnlinePinResult, OnlinePINError> errOrResult =
                        client.onlinePin(
                                getInterfaceType(), amountInPennies, currencyCode, maskedTrack2Data,
                                applicationLabel);

                if (errOrResult.isSuccess()) {
                    MpiClient.OnlinePinResult result = errOrResult.asSuccess().getValue();
                    switch (result.mType) {
                        case CancelOrTimeout:
                            listener.onCancelOrTimeout();
                            break;
                        case BypassedPinEntry:
                            listener.onBypassedPINEntry();
                            break;
                        case PinEnteredOk:
                            if (result.PinData == null || result.PinKsn == null) {
                                listener.onError(OnlinePINError.INTERNAL_ERROR);
                                return;
                            }
                            listener.onOnlinePIN(result.PinData, result.PinKsn);
                            break;
                    }
                } else {
                    listener.onError(errOrResult.asError().getError());
                }
            }
        });
    }

    /**
     * Starts Contactless transaction
     *
     * @param transactionType The {@link TransactionType}
     * @param amountInPennies Amount in pennies
     * @param currencyCode    ISO 4217 code, see here for details: https://en.wikipedia
     *                        .org/wiki/ISO_4217
     * @param listener        {@link ApiStartTransactionListener} Event listener for result -
     *                        contains result byte data
     */
    public void startContactlessTransaction(
            @NonNull TransactionType transactionType,
            int amountInPennies,
            int currencyCode,
            @NonNull final ApiStartTransactionListener listener) {
        startContactlessTransaction(transactionType, amountInPennies, currencyCode, null, listener);
    }

    /**
     * Start the contactless transaction specifying a language preference for the terminal.
     * This indicates which messages to show during the contactless transaction.
     *
     * @param transactionType    The {@link TransactionType}
     * @param amountInPennies    Amount in pennies
     * @param currencyCode       ISO 4217 code, see here for details: https://en.wikipedia
     *                           .org/wiki/ISO_4217
     * @param languagePreference 2 digit country code based on ISO 3166, but will match with the
     *                           config file name installed on the device.
     * @param listener           {@link ApiStartTransactionListener} Event listener for result -
     *                           contains result byte data
     */
    public void startContactlessTransaction(
            @NonNull final TransactionType transactionType,
            final int amountInPennies,
            final int currencyCode,
            @Nullable final String languagePreference,
            @NonNull final ApiStartTransactionListener listener
    ) {
        executeAsync(new AsyncRunnable() {
            @Override
            public void runOnAsyncThread(@NonNull MpiClient client) {
                Result<byte[], TransactionResponse> response =
                        client.startContactlessTransaction(
                                getInterfaceType(),
                                transactionType, amountInPennies, currencyCode, languagePreference);
                if (response.isSuccess()) {
                    listener.onSuccess(response.asSuccess().getValue());
                } else {
                    listener.onError(response.asError().getError());
                }
            }
        });
    }

    /**
     * Starts EMV Contact transaction
     *
     * @param transactionType The {@link TransactionType}
     * @param amountInPennies Amount in pennies
     * @param currencyCode    ISO 4217 code, see here for details: https://en.wikipedia
     *                        .org/wiki/ISO_4217
     * @param listener        {@link ApiStartTransactionListener} Event listener for result -
     *                        contains result byte data
     */
    public void startTransaction(
            @NonNull final TransactionType transactionType,
            final int amountInPennies,
            final int currencyCode,
            @NonNull final ApiStartTransactionListener listener
    ) {
        executeAsync(new AsyncRunnable() {
            @Override
            public void runOnAsyncThread(@NonNull MpiClient client) {
                Result<byte[], TransactionResponse> response = client.startTransaction(
                        getInterfaceType(),
                        transactionType, amountInPennies, currencyCode);

                if (response.isSuccess()) {
                    listener.onSuccess(response.asSuccess().getValue());
                } else {
                    listener.onError(response.asError().getError());
                }
            }
        });
    }

    /**
     * Abort Transaction command will stop any contact or contactless transaction processing and
     * return the PED to a state ready to receive the next command.
     *
     * @param listener {@link MiuraDefaultListener} Event listener for the result.
     */
    public void abortTransaction(@Nullable final MiuraDefaultListener listener) {

        if (mAbortExecutor == null) {
            return;
        }

        LOGGER.debug("MiuraManager: abortTransaction");

        executeAsyncDefaultListener(mAbortExecutor, listener, new AsyncBooleanRunnable() {
            @Override
            public boolean runOnAsyncThread(@NonNull MpiClient client) {
                boolean b = client.abortTransaction(getInterfaceType());
                LOGGER.debug("abortTransaction: aborted");
                return b;
            }
        });
    }

    /**
     * Continue Transaction with data in {@link TLVObject}
     * This command is typically used to conclude the transaction after an online approval stage.
     * This can be used the same for Contact and contactless transactions.
     *
     * @param tlvObjects List of {@link TLVObject}
     * @param listener   {@link ApiContinueTransactionListener} Event listener for result with
     *                   continue transaction result byte data
     */
    public void continueTransaction(
            @NonNull final ArrayList<TLVObject> tlvObjects,
            @NonNull final ApiContinueTransactionListener listener
    ) {
        executeAsync(new AsyncRunnable() {
            @Override
            public void runOnAsyncThread(@NonNull MpiClient client) {
                TLVObject tlv = new TLVObject(Description.Command_Data, tlvObjects);
                Result<byte[], TransactionResponse> result =
                        client.continueTransaction(getInterfaceType(), tlv);
                if (result.isSuccess()) {
                    listener.onSuccess(result.asSuccess().getValue());
                } else {
                    listener.onError(result.asError().getError());
                }
            }
        });
    }

    /**
     * Queries the peripherals connected to an ITP or POSzle device.
     * These may be a scanner printer, PED or other deice.
     * The listener is called with the result in the form of a list of strings.
     * Currently the following are defined:
     * Printer
     * Bar Code Scanner
     * PED
     * NOTE: This list may be zero length. This is not an error, it means no devices are connected.
     *
     * @param listener {@link ApiPeripheralTypeListener} Event listener Peripheral Type
     */
    public void peripheralStatusCommand(@NonNull final ApiPeripheralTypeListener listener) {

        if (mMpiClient == null) {
            return;
        }

        executeAsync(new AsyncRunnable() {
            @Override
            public void runOnAsyncThread(@NonNull MpiClient client) {
                ArrayList<String> peripheralTypes = client.peripheralStatusCommand();
                if (peripheralTypes != null) {
                    listener.onSuccess(peripheralTypes);
                } else {
                    listener.onError();
                }
            }
        });

    }

    /**
     * Returns the status of the PED's payment encryption key injection process.
     *
     * @param listener {@link ApiP2PEStatusListener} Event listener with P2PE status
     */
    public void getP2PEStatus(@NonNull final ApiP2PEStatusListener listener) {
        executeAsync(new AsyncRunnable() {
            @Override
            public void runOnAsyncThread(@NonNull MpiClient client) {
                P2PEStatus status = client.p2peStatus(getInterfaceType());
                if (status != null) {
                    listener.onSuccess(status);
                } else {
                    listener.onError();
                }
            }
        });
    }

    /**
     * Instructs the PED to prepare the files required for a HSM to exchange keys.
     * This process can take up to 2 minutes, but usually takes around 30 seconds.
     *
     * @param listener {@link MiuraDefaultListener} Event listener for result
     */
    public void P2PEInitialise(@Nullable final MiuraDefaultListener listener) {
        executeAsyncDefaultListener(listener, new AsyncBooleanRunnable() {
            @Override
            public boolean runOnAsyncThread(@NonNull MpiClient client) {
                return client.p2peInitialise(getInterfaceType());
            }
        });
    }

    /**
     * Import and inject the keys loaded with the upload file command.
     *
     * @param listener {@link MiuraDefaultListener} Event listener for the result.
     */
    public void P2PEImport(@NonNull final ApiP2PEImportListener listener) {
        executeAsync(new AsyncRunnable() {
            @Override
            public void runOnAsyncThread(@NonNull MpiClient client) {
                RKIError status = client.p2peImport(getInterfaceType());
                if (status == RKIError.NoError) {
                    listener.onSuccess();
                } else {
                    listener.onError(status);
                }
            }
        });
    }

    /**
     * Enable or disable reading data form scanner
     *
     * <p>
     * Enables/disables {@link MpiEvents#BarcodeScanned} events.
     * </p>
     *
     * @param enabled  Flag for enable or disable
     * @param listener {@link MiuraDefaultListener} Event listener for result
     */
    public void barcodeScannerStatus(
            final boolean enabled,
            @Nullable final MiuraDefaultListener listener
    ) {
        executeAsyncDefaultListener(listener, new AsyncBooleanRunnable() {
            @Override
            public boolean runOnAsyncThread(@NonNull MpiClient client) {
                return client.barcodeStatus(InterfaceType.RPI, enabled);
            }
        });
    }

    /**
     * Adds text to the print spool on the device
     *
     * @param text     Text to print in ASCII
     * @param listener {@link MiuraDefaultListener} Event listener for result
     */
    public void spoolText(
            @NonNull final String text,
            @Nullable final MiuraDefaultListener listener
    ) {
        executeAsyncDefaultListener(listener, new AsyncBooleanRunnable() {
            @Override
            public boolean runOnAsyncThread(@NonNull MpiClient client) {
                return client.spoolText(getInterfaceType(), text);
            }
        });

    }

    /**
     * Add image to the print spool on device.
     *
     * @param imageFileName Image path on device. Image should be first uploaded to the POS device
     *                      by
     *                      {@link MiuraManager#uploadBinary(byte[], String, MiuraDefaultListener)}
     *                      or {@link MiuraManager#uploadBinary(File, MiuraDefaultListener)}. It
     *                      should
     *                      Then be installed using
     *                      {@link MiuraManager#hardReset(MiuraDefaultListener)}
     * @param listener      {@link MiuraDefaultListener} Event listener for result
     */
    public void spoolImage(
            @NonNull final String imageFileName,
            @Nullable final MiuraDefaultListener listener
    ) {
        executeAsyncDefaultListener(listener, new AsyncBooleanRunnable() {
            @Override
            public boolean runOnAsyncThread(@NonNull MpiClient client) {
                return client.spoolImage(getInterfaceType(), imageFileName);
            }
        });
    }

    /**
     * Print the spool buffer to the connected printer.
     * The print spool must first be filled up with calls to {@link MiuraManager#spoolImage(String,
     * MiuraDefaultListener)}
     * or {@link MiuraManager#spoolText(String, MiuraDefaultListener)}
     *
     * @param listener {@link MiuraDefaultListener} Event listener for result
     */
    public void spoolPrint(@Nullable final MiuraDefaultListener listener) {
        executeAsyncDefaultListener(listener, new AsyncBooleanRunnable() {
            @Override
            public boolean runOnAsyncThread(@NonNull MpiClient client) {
                return client.spoolPrint(getInterfaceType());
            }
        });
    }

    /**
     * Print ESC/POS data to the printer
     * This will allow printing immediately at every LF (line feed) character sent to printer.
     * For more information see the developer guide
     *
     * @param text     String formatted with ESCPOS commands to send to the printer.
     * @param listener {@link MiuraDefaultListener} Event listener for result
     */
    public void printESCPOSWithString(
            @NonNull final String text,
            @Nullable final MiuraDefaultListener listener
    ) {
        executeAsyncDefaultListener(listener, new AsyncBooleanRunnable() {
            @Override
            public boolean runOnAsyncThread(@NonNull MpiClient client) {
                return client.printESCPOScommand(getInterfaceType(), text);
            }
        });
    }

    /**
     * Prints text on POS device
     *
     * @param text     Text to print in ASCII
     * @param listener {@link MiuraDefaultListener} Event listener for result
     */
    public void printText(
            @NonNull final String text,
            @Nullable final MiuraDefaultListener listener
    ) {
        executeAsyncDefaultListener(listener, new AsyncBooleanRunnable() {
            @Override
            public boolean runOnAsyncThread(@NonNull MpiClient client) {
                return client.printText(getInterfaceType(), text, false);
            }
        });
    }

    /**
     * Prints image on POS device
     *
     * @param image    Image path on device. Image should be first uploaded to the POS device by
     *                 {@link MiuraManager#uploadBinary(byte[], String, MiuraDefaultListener)}
     *                 or {@link MiuraManager#uploadBinary(File, MiuraDefaultListener)}
     * @param listener {@link MiuraDefaultListener} Event listener for result
     */
    public void printImage(
            @NonNull final String image,
            @Nullable final MiuraDefaultListener listener
    ) {
        executeAsyncDefaultListener(listener, new AsyncBooleanRunnable() {
            @Override
            public boolean runOnAsyncThread(@NonNull MpiClient client) {
                return client.printImage(getInterfaceType(), image);
            }
        });
    }

    /**
     * Queries status of the cash drawer, and optionally requests the cash drawer to open.
     *
     * @param openCashDrawer Additionally open cash drawer
     * @param listener       {@link ApiCashDrawerListener} Event listener for result with cash
     *                       drawer open status
     */
    public void cashDrawer(
            final boolean openCashDrawer,
            @NonNull final ApiCashDrawerListener listener
    ) {
        executeAsync(new AsyncRunnable() {
            @Override
            public void runOnAsyncThread(@NonNull MpiClient client) {
                CashDrawer drawStatus = client.cashDrawer(openCashDrawer);
                if (drawStatus != null) {
                    listener.onSuccess(drawStatus == CashDrawer.Opened);
                } else {
                    listener.onError();
                }
            }
        });
    }


    /**
     * Enable or disable the status messages from the M012 printer sled.
     *
     * <p>
     * Enables/disables {@link MpiEvents#PrinterStatusChanged} events.
     * </p>
     *
     * @param printerSledStatusEnabled Enable or disable the printer sled status messages
     * @param listener                 {@link MiuraDefaultListener} Event listener for result
     */
    public void printerSledStatus(
            final boolean printerSledStatusEnabled,
            @Nullable final MiuraDefaultListener listener
    ) {
        executeAsyncDefaultListener(listener, new AsyncBooleanRunnable() {
            @Override
            public boolean runOnAsyncThread(@NonNull MpiClient client) {
                return client.printerSledStatus(
                        getInterfaceType(), printerSledStatusEnabled);
            }
        });
    }

    /**
     * Returns name & address of POSzle device
     *
     * This is used when USB lead is attached to a POSzle device/over
     * bluetooth to get name & address(Mac) of the device, HashMap
     * values include: 'name' & address'.
     *
     * @param listener {@link ApiBlueToothInfoListener} Event listener for result
     */
    public void getBluetoothInfo(@NonNull final ApiBlueToothInfoListener listener) {
        executeAsync(new AsyncRunnable() {
            @Override
            public void runOnAsyncThread(@NonNull MpiClient client) {
                HashMap<String, String> bluetoothInfo = client.getBluetoothInfo();
                if (bluetoothInfo != null) {
                    listener.onSuccess(bluetoothInfo);
                } else {
                    listener.onError();
                }
            }
        });
    }

    /**
     * Send the config details to the POS device to configure a connected USB to serial converter
     * <p>
     * Use the SerialPortProperties class to specify the settings required for the peripheral
     * connected and use this method to send the setting to the POS device.
     * </p>
     *
     * @param serialPortProperties {@link SerialPortProperties} Class used to describe the serial
     *                             port settings
     * @param listener             {@link MiuraDefaultListener} Result listener
     */
    public void configureSerialPort(
            @NonNull final SerialPortProperties serialPortProperties,
            @Nullable final MiuraDefaultListener listener
    ) {
        executeAsyncDefaultListener(listener, new AsyncBooleanRunnable() {
            @Override
            public boolean runOnAsyncThread(@NonNull MpiClient client) {
                return client.setSerialPort(serialPortProperties);
            }
        });
    }

    /**
     * Send a data packet to a device connected to the USB to serial converter.
     * <p>
     * Send a buffer of data to a device connected via a USB to serial converter connected to the
     * POS device.
     * The data buffer should be a maximum of 250 bytes of data.
     * The success result will be called once the data has been written to the serial port by the
     * POS device.
     * If there is no serial adaptor connected the onError of the listener will be called.
     * </p>
     *
     * @param data     Data buffer to send to device
     * @param listener {@link MiuraDefaultListener}
     */
    public void sendDataToSerialPort(
            @NonNull final byte[] data,
            @Nullable final MiuraDefaultListener listener
    ) {
        executeAsyncDefaultListener(listener, new AsyncBooleanRunnable() {
            @Override
            public boolean runOnAsyncThread(@NonNull MpiClient client) {
                return client.sendDataToSerialPort(data);
            }
        });
    }

    /**
     * Request numerical input from user.
     *
     * See {@link MpiClient#getNumericData for more}
     *
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
     * @param listener          Listener callback.
     */
    public void getNumericData(
            final boolean automaticEnter,
            final boolean backlightOn,
            final int firstLineIndex,
            final int secondLineIndex,
            final int thirdLineIndex,
            final int numIntDigits,
            final int numFracDigits,
            @Nullable final String numberToEditAscii,
            @Nullable final Integer currencyCode,
            @Nullable final Integer currencyExponent,
            @Nullable final Integer amountInPennies,
            @Nullable final Integer amountLine,
            final ApiGetNumericDataListener listener
    ) {
        final InterfaceType interfaceType = getInterfaceType();

        executeAsync(new AsyncRunnable() {
            @Override
            public void runOnAsyncThread(@NonNull MpiClient client) {
                try {
                    Result<String, GetNumericDataError> result = client.getNumericData(
                            interfaceType,
                            automaticEnter, backlightOn, firstLineIndex, secondLineIndex,
                            thirdLineIndex, numIntDigits, numFracDigits, numberToEditAscii,
                            amountInPennies, currencyCode, currencyExponent, amountLine
                    );
                    if (result.isSuccess()) {
                        listener.onSuccess(result.asSuccess().getValue());
                    } else {
                        listener.onError(result.asError().getError());
                    }
                } catch (Throwable throwable) {
                    LOGGER.warn("Exception in getNumericData?", throwable);
                    listener.onError(GetNumericDataError.InternalError);
                }
            }
        });
    }


    /**
     * The given runnable will be scheduled to run in the background on MiuraManager's async thread.
     *
     * <p>
     * This runnable will be executed after any previously scheduled tasks have finished,
     * including the ones implicitly scheduled by commands such as
     * e.g. {@note #configureSerialPort}
     * </p>
     *
     * @param runnable Task to run
     */
    public void executeAsync(final AsyncRunnable runnable) {
        final MpiClient mpiClient = mMpiClient;
        ExecutorService executor = mExecutor;
        if (mpiClient == null || executor == null) {
            return;
        }

        /*
            todo thread issue: executor could be shutdown at this point.
            Do we have to handle that? i.e. will it throw an exception?
        */
        executor.execute(new Runnable() {
            @Override
            public void run() {
                runnable.runOnAsyncThread(mpiClient);
            }
        });
    }

    /**
     * Runs a given task in the background and calls the MiuraDefaultListener afterwards
     *
     * <p>
     * All async methods using MiuraDefaultListener will have the same code to call the listeners.
     * This method reduces the amount of boilerplate and automatically
     * calls the correct method in MiuraDefaultListener depending on the result of
     * {@code task.run()}.
     * </p>
     *
     * @param listener Default listener
     * @param task     Task to run
     */
    private void executeAsyncDefaultListener(
            @Nullable final MiuraDefaultListener listener,
            @NonNull final AsyncBooleanRunnable task
    ) {
        executeAsyncDefaultListener(mExecutor, listener, task);
    }

    /**
     * Runs a given task on the given executor and calls the MiuraDefaultListener afterwards
     *
     * <p>
     * All async methods using MiuraDefaultListener will have the same code to call the listeners.
     * This method reduces the amount of boilerplate and automatically
     * calls the correct method in MiuraDefaultListener depending on the result of
     * {@code task.run()}.
     * </p>
     *
     * @param executor The executor to schedule the task on
     * @param listener Default listener
     * @param task     Task to run
     */
    private void executeAsyncDefaultListener(
            @Nullable ExecutorService executor,
            @Nullable final MiuraDefaultListener listener,
            @NonNull final AsyncBooleanRunnable task
    ) {
        final MpiClient mpiClient = mMpiClient;
        if (executor == null || mpiClient == null) {
            return;
        }
        executor.execute(new Runnable() {
            @Override
            public void run() {
                boolean result = task.runOnAsyncThread(mpiClient);
                if (listener == null) {
                    return;
                }
                if (result) {
                    listener.onSuccess();
                } else {
                    listener.onError();
                }
            }
        });
    }

    @NonNull
    public static MiuraManager getInstance() {
        if (sInstance == null) {
            sInstance = new MiuraManager();
        }
        return sInstance;
    }

    public enum DeviceType {
        PED, POS
    }

    /**
     * A task that runs in the background on MiuraManager's async threads.
     */
    public interface AsyncRunnable {
        /**
         * The task to run.
         *
         * @param client The commands should be executed on this client
         */
        void runOnAsyncThread(@NonNull MpiClient client);
    }

    /**
     * A task that runs in the background on MiuraManager's async threads and returns a result.
     */
    interface AsyncBooleanRunnable {
        /**
         * The task to run.
         *
         * @param client The commands should be executed on this client
         * @return true: Returns in a call to MiuraDefaultListener.onSuccess(). False: onError()
         */
        boolean runOnAsyncThread(@NonNull MpiClient client);
    }

}

/**
 * Creates new threads for MiuraManager's executor services.
 */
class MiuraManagerThreadFactory implements ThreadFactory {

    /** Each thread created is given a unique number. */
    private final AtomicInteger threadNumber = new AtomicInteger(0);
    /**
     * The prefix to give each thread's name.
     */
    private final String mThreadPrefix;

    /**
     * Create a new MiuraManagerThreadFactory.
     *
     * @param prefix A thread's name will be "MiuraManager-PREFIX-ID"
     */
    MiuraManagerThreadFactory(String prefix) {
        mThreadPrefix = prefix;
    }

    @Override
    public Thread newThread(@NonNull Runnable r) {
        int id = threadNumber.getAndIncrement();
        String name = "MiuraManager-" + mThreadPrefix + "-" + id;
        Thread t = new Thread(r, name);
        if (t.isDaemon()) {
            t.setDaemon(false);
        }
        // if (t.getPriority() != Thread.NORM_PRIORITY)
        //    t.setPriority(Thread.NORM_PRIORITY);
        return t;
    }
}

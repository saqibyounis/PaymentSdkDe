/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.examples.connectors;


import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.intel.bluetooth.BluetoothConsts;
import com.miurasystems.miuralibrary.comms.Connector;
import com.miurasystems.miuralibrary.comms.MpiProtocolSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.SynchronousQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.bluetooth.BluetoothStateException;
import javax.bluetooth.DiscoveryAgent;
import javax.bluetooth.LocalDevice;
import javax.bluetooth.RemoteDevice;
import javax.bluetooth.ServiceRecord;
import javax.bluetooth.UUID;
import javax.microedition.io.StreamConnection;


public final class JavaBluetoothConnector extends Connector {

    private static final Logger LOGGER = LoggerFactory.getLogger(JavaBluetoothConnector.class);
    private static final Pattern COLON_PATTERN = Pattern.compile(":", Pattern.LITERAL);
    private static final Pattern BT_ADDR_PATTERN = Pattern.compile("[0-9A-F]{12}");

    @NonNull
    private final String mUrl;

    @Nullable
    private OutputStream mOutputStream;

    @Nullable
    private InputStream mInputStream;

    @Nullable
    private StreamConnection mConnection;

    public JavaBluetoothConnector(@NonNull String url) {
        LOGGER.trace("new JavaBluetoothConnector({})", url);

        mUrl = url;
        mOutputStream = null;
        mInputStream = null;
        mConnection = null;
    }

    @Override
    public boolean isConnected() {
        LOGGER.trace("isConnected()");
        return (mConnection != null) && (mOutputStream != null) && (mInputStream != null);
    }

    @Override
    protected void connect() throws IOException {
        LOGGER.trace("connect()");

        if (isConnected()) {
            disconnect();
        }

        LOGGER.debug("connecting to {} ...", mUrl);

        mConnection = (StreamConnection) javax.microedition.io.Connector.open(mUrl);
        mOutputStream = mConnection.openOutputStream();
        mInputStream = mConnection.openInputStream();

        LOGGER.debug("... streams open");
    }

    @Override
    protected void disconnect(@NonNull MpiProtocolSession closingSession) throws IOException {
        LOGGER.trace("disconnect({})", closingSession);

        // Just ignore closingSession
        disconnect();
    }

    private void disconnect() throws IOException {
        LOGGER.debug("disconnecting...");

        if (mInputStream != null) {
            mInputStream.close();
            mInputStream = null;
        }
        if (mOutputStream != null) {
            mOutputStream.close();
            mOutputStream = null;
        }
        if (mConnection != null) {
            mConnection.close();
            mConnection = null;
        }

        try {
            // If we don't have this sleep here then we can't always re-open it straight away
            // Sometimes even 1ms is fine, but sometimes too quick. 20ms seems ok?
            Thread.sleep(50L);
        } catch (InterruptedException e) {
            LOGGER.debug("Unexpected interrupt exception", e);
        }

        LOGGER.debug("... streams closed");
    }

    @NonNull
    @Override
    protected InputStream getInputStream() throws IOException {
        LOGGER.trace("getInputStream()");

        if (mInputStream == null) {
            disconnect();
            throw new IOException("Connection not open");
        }
        return mInputStream;
    }

    @NonNull
    @Override
    protected OutputStream getOutputStream() throws IOException {
        LOGGER.trace("getOutputStream()");

        if (mOutputStream == null) {
            disconnect();
            throw new IOException("Connection not open");
        }
        return mOutputStream;
    }

    @Nullable
    private static String convertAddress(String givenAddress) {
        // device.getBluetoothAddress() will return: "12 chars, Valid characters are 0-9 and A-F"
        Matcher colonMatcher = COLON_PATTERN.matcher(givenAddress);
        String upper = colonMatcher.replaceAll("").toUpperCase();

        Matcher addressMatcher = BT_ADDR_PATTERN.matcher(upper);
        return addressMatcher.matches() ? upper : null;
    }

    @Nullable
    private static RemoteDevice[] getRemoteDevices(int option) throws BluetoothStateException {
        /*
        todo refactor DiscoveryAgent in a graceful way
            docs say getLocalDevice always returns same object.
            docs say getDiscoveryAgent always returns same object.
            Therefore we can move it to static init time? Only problem is BluetoothStateException?
            Can't have that going off if computer doesn't support bluetooth.
        */
        LocalDevice localDevice = LocalDevice.getLocalDevice();
        DiscoveryAgent agent = localDevice.getDiscoveryAgent();
        return agent.retrieveDevices(option);
    }

    @Nullable
    public static RemoteDevice findDeviceForAddress(String givenAddress) throws IOException {
        LOGGER.trace("findDeviceForAddress({})", givenAddress);

        String convertedAddress = convertAddress(givenAddress);
        if (convertedAddress == null) {
            throw new IOException("Invalid BT Address: " + givenAddress);
        }

        LOGGER.trace("findDeviceForAddress: Searching CACHED");
        RemoteDevice[] cachedDevices = getRemoteDevices(DiscoveryAgent.CACHED);
        RemoteDevice device = findDeviceForAddressImpl(convertedAddress, cachedDevices);
        if (device != null) {
            return device;
        }

        LOGGER.trace("findDeviceForAddress: Now searching PREKNOWN");
        RemoteDevice[] preKnownDevices = getRemoteDevices(DiscoveryAgent.PREKNOWN);
        return findDeviceForAddressImpl(convertedAddress, preKnownDevices);
    }

    @Nullable
    private static RemoteDevice findDeviceForAddressImpl(
            String convertedAddress,
            @Nullable RemoteDevice[] remoteDevices
    ) {
        if (remoteDevices == null) {
            LOGGER.trace("findDeviceForAddressImpl: remoteDevices == null!");
            return null;
        }
        for (RemoteDevice device : remoteDevices) {
            String deviceHardwareAddress = device.getBluetoothAddress();
            if (deviceHardwareAddress.equals(convertedAddress)) {
                return device;
            }
        }

        return null;
    }

    @Nullable
    public static RemoteDevice findDeviceForName(String matchDeviceName) throws IOException {
        LOGGER.trace("findDeviceForName({})", matchDeviceName);

        LOGGER.trace("findDeviceForName: Searching CACHED");
        RemoteDevice[] cachedDevices = getRemoteDevices(DiscoveryAgent.CACHED);
        RemoteDevice device = findDeviceForNameImpl(matchDeviceName, cachedDevices);
        if (device != null) {
            return device;
        }

        LOGGER.trace("findDeviceForName: Now searching PREKNOWN");
        RemoteDevice[] preKnownDevices = getRemoteDevices(DiscoveryAgent.PREKNOWN);
        return findDeviceForNameImpl(matchDeviceName, preKnownDevices);
    }

    @Nullable
    private static RemoteDevice findDeviceForNameImpl(
            String matchDeviceName,
            @Nullable RemoteDevice[] remoteDevices
    ) throws IOException {
        if (remoteDevices == null) {
            LOGGER.trace("findDeviceForNameImpl: remoteDevices == null!");
            return null;
        }

        for (RemoteDevice device : remoteDevices) {
            String deviceName = device.getFriendlyName(false);
            if (deviceName.toLowerCase().equals(matchDeviceName.toLowerCase())) {
                return device;
            }
        }

        return null;
    }

    static String getAllDevicesString() throws IOException {
        LOGGER.trace("getAllDevicesString()");

        LOGGER.trace("getAllDevicesString: Searching CACHED");
        RemoteDevice[] cachedDevices = getRemoteDevices(DiscoveryAgent.CACHED);
        String cachedDevicesString = getAllDevicesStringImpl(cachedDevices);
        if (cachedDevicesString != null) {
            return cachedDevicesString;
        }

        LOGGER.trace("getAllDevicesString: Searching PREKNOWN");
        RemoteDevice[] preKnownDevices = getRemoteDevices(DiscoveryAgent.PREKNOWN);
        String preKnownDevicesString = getAllDevicesStringImpl(preKnownDevices);
        if (preKnownDevicesString != null) {
            return preKnownDevicesString;
        }

        LOGGER.trace("getAllDevicesString: Can't find any devices?");
        return "NO BT DEVICES FOUND";
    }

    @Nullable
    private static String getAllDevicesStringImpl(
            @Nullable RemoteDevice[] remoteDevices
    ) throws IOException {
        if (remoteDevices == null) {
            LOGGER.trace("getAllDevicesStringImpl: remoteDevices == null!");
            return null;
        }

        StringBuilder builder = new StringBuilder(remoteDevices.length * 64);
        builder.append("[\n");

        for (RemoteDevice device : remoteDevices) {
            String deviceName = device.getFriendlyName(false);
            String deviceAddress = device.getBluetoothAddress();

            String fmt = String.format("    BTDevice(%s, %s)\n", deviceName, deviceAddress);
            builder.append(fmt);
        }
        builder.append(']');

        return builder.toString();
    }

    public static String getUrlForDevice(RemoteDevice chosenDevice) throws IOException {
        LOGGER.trace("getUrlForDevice({})", chosenDevice);

        LocalDevice localDevice = LocalDevice.getLocalDevice();
        DiscoveryAgent agent = localDevice.getDiscoveryAgent();

        LOGGER.info(getAllDevicesString());

        SynchronousQueue<ServiceRecordQueueInfo> queue = new SynchronousQueue<>();

        UUID[] uuidSet = {BluetoothConsts.SERIAL_PORT_UUID};
        MyDiscoveryListener listener = new MyDiscoveryListener(queue, chosenDevice);
        agent.searchServices(null, uuidSet, chosenDevice, listener);

        String deviceString = remoteDeviceToString(chosenDevice);

        ServiceRecordQueueInfo info;
        try {
            LOGGER.debug("wait for {}", deviceString);
            info = queue.take();
            LOGGER.debug("{}: Queue returned", deviceString);
        } catch (InterruptedException e) {
            String fmt = "Interrupted whilst waiting for serviceRecord for " + deviceString;
            throw new IOException(fmt, e);
        }

        if (!info.mRemoteDevice.equals(chosenDevice)) {
            String fmt = String.format(
                    "Unexpected device in queue?? Expecting %s, got %s???!",
                    deviceString, remoteDeviceToString(chosenDevice)
            );
            throw new IOException(fmt);
        } else if (!info.mRecordAvailable) {
            String format = String.format(
                    "Failed to get URL for %s due to %s",
                    deviceString, info.mErrorResponseCode
            );
            throw new IOException(format);
        }

        ServiceRecord serviceRecord = info.mServiceRecord;
        if (serviceRecord == null) {
            throw new AssertionError("Bad queue info. null service but mRecordAvailable == true");
        }
        boolean mustBeMaster = false;
        return serviceRecord.getConnectionURL(ServiceRecord.AUTHENTICATE_ENCRYPT, mustBeMaster);

        // server:
        // btspp://locahost:<UUID>[;<param>=<value>;...;<param>=<value>]

        // client:
        // btspp://<BD_ADDR>:<SRV_CH_ID>[;<param>=<value>;...;<param>=<value>]
        // return String.format("btspp://%s:%s;%s", chosenDevice.getBluetoothAddress(), 1, "");
    }

    private static String remoteDeviceToString(RemoteDevice chosenDevice) throws IOException {
        String friendlyName = chosenDevice.getFriendlyName(false);
        String bluetoothAddress = chosenDevice.getBluetoothAddress();
        return String.format("%s/%s", bluetoothAddress, friendlyName);
    }
}

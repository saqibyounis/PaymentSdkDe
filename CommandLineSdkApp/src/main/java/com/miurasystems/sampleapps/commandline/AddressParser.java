/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.sampleapps.commandline;


import android.support.annotation.Nullable;

import com.miurasystems.examples.connectors.ClientSocketConnector;
import com.miurasystems.examples.connectors.JavaBluetoothConnector;
import com.miurasystems.miuralibrary.comms.Connector;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.util.regex.Pattern;

import javax.bluetooth.RemoteDevice;


final class AddressParser {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(AddressParser.class);

    // todo pyMPI-Test also has looksLikeBTIndex for posix. Do we want/need?

    private static final Pattern BT_ADDR = Pattern.compile("([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}");
    private static final Pattern BT_ADDR_FLAT = Pattern.compile("[0-9a-fA-F]{12}");
    private static final Pattern POSIX_SERIAL_DEVICE = Pattern.compile("/dev/.*");
    private static final Pattern WINDOWS_SERIAL_DEVICE = Pattern.compile("[cC][oO][mM][0-9]+");
    private static final Pattern IP_ADDRESS_PATTERN =
            Pattern.compile("(\\d{1,3}\\.){3}\\d{1,3}:\\d{1,5}");

    private AddressParser() {
    }

    @Nullable
    private static RemoteDevice checkIfBtName(String name) {
        try {
            return JavaBluetoothConnector.findDeviceForName(name);
        } catch (IOException ignore) {
            return null;
        }
    }

    private static boolean checkIfBtAddress(String addr) {
        return BT_ADDR.matcher(addr).matches() || BT_ADDR_FLAT.matcher(addr).matches();
    }

    private static boolean checkIfSerialDevice(String serial) {
        if (SystemUtils.IS_OS_UNIX) {
            return POSIX_SERIAL_DEVICE.matcher(serial).matches();
        } else {
            return WINDOWS_SERIAL_DEVICE.matcher(serial).matches();
        }
    }

    private static boolean checkIfIpAddress(String addr) {
        return IP_ADDRESS_PATTERN.matcher(addr).matches();
    }

    static Connector parseDeviceAddress(String givenAddress)
            throws CommandLineUsageException, IOException {

        String address = givenAddress.trim();

        if (checkIfIpAddress(address)) {

            String[] split = address.split(":");
            if (split.length != 2) {
                throw new CommandLineUsageException("Invalid IP Address. Should be <ip>:<port>");
            }

            InetAddress addr = InetAddress.getByName(split[0]);

            int port;
            try {
                port = Integer.parseInt(split[1]);
            } catch (NumberFormatException exception) {
                throw new CommandLineUsageException("Invalid port: " + split[1], exception);
            }

            return new ClientSocketConnector(addr, port, 1250);
        }

        if (checkIfSerialDevice(address)) {
            throw new NotImplementedException("Serial device Connector not supported yet");
        }

        RemoteDevice btName = checkIfBtName(address);
        if ((btName != null) || checkIfBtAddress(address)) {

            RemoteDevice device;
            if (btName != null) {
                device = btName;
            } else {
                device = JavaBluetoothConnector.findDeviceForAddress(address);
            }
            if (device == null) {
                String fmt = "Unknown Bluetooth device: '%s'. Did you pair to it first?";
                throw new IOException(String.format(fmt, address));
            }

            String url = JavaBluetoothConnector.getUrlForDevice(device);
            return new JavaBluetoothConnector(url);
        }

        throw new CommandLineUsageException("Unknown device address type: " + address);
    }

    static void getUsage(StringBuilder builder) {
        //noinspection HardcodedFileSeparator
        String s = "    Bluetooth name         : 'Miura 049'\n"
                + "    Bluetooth address      : '00:1B:10:00:0F:08'\n"
                + "    Wi-Fi (IP address:port): '192.168.0.100:6543'\n"
                + "    Wi-Fi (IP server port) : ':3456'\n"
                + "    Serial port (Windows)  : 'COM5'\n"
                + "    Serial port (Linux)    : '/dev/rfcomm0'\n"
                + "    Local Socket (M20)     : ? Not implemented yet ?";
        builder.append(s);
    }
}

/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.sampleapps.commandline;

import com.miurasystems.examples.connectors.ClientSocketConnector;
import com.miurasystems.examples.connectors.JavaBluetoothConnector;
import com.miurasystems.miuralibrary.comms.Connector;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.SystemUtils;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Requires Bluetooth to work on your computer
 * And for specific BT devices to be paired.
 * Not the greatest test in the world...
 */
// todo figure out a way to mock/test this class properly
@RunWith(JUnitParamsRunner.class)
public class AddressParserTest {

    /*
    Not implemented yet:
        Wi-Fi (IP address/port): "192.168.0.100:6543"
        Wi-Fi (IP server port) : ":3456"
        Serial port (Windows)  : "COM5"
        Serial port (Linux)    : "/dev/rfcomm0"
     */

    @Ignore("Requires live BT stack")
    @Parameters({
            "Miura 515",
            "\tMiura 515    ",
            "5C:F3:70:62:C1:3E",
            "5CF37062C13E",
            "5c:f3:70:62:c1:3e",
            "5cf37062c13e",
    })
    @Test
    public void parseAddressBluetoothGood(String givenBtAddress) throws Exception {
        // setup

        // execute
        Connector actualConnector = AddressParser.parseDeviceAddress(givenBtAddress);

        // verify
        assertThat(actualConnector.getClass(), is(equalTo(JavaBluetoothConnector.class)));
    }

    @Ignore("Requires live BT stack")
    @Test
    public void parseAddressBluetoothBad() throws Exception {
        // setup
        String givenBtAddress = "A0:B1:C2:D3:E4:F5";

        // execute
        try {
            Connector actualConnector = AddressParser.parseDeviceAddress(givenBtAddress);
            Assert.fail("Expecting " + givenBtAddress + " to be an unknown device!");
        } catch (IOException exception) {
            String lower = exception.getMessage().toLowerCase();
            assertThat(lower, containsString("unknown"));
            assertThat(lower, containsString(givenBtAddress.toLowerCase()));
        }
    }

    @Ignore("Requires live BT stack")
    @Test
    public void parseAddressUnknownType() throws Exception {
        // setup
        String givenAddress = "192.abc:d3:12345";

        // execute
        try {
            Connector actualConnector = AddressParser.parseDeviceAddress(givenAddress);
        } catch (CommandLineUsageException exception) {
            // verify
            String lower = exception.getMessage().toLowerCase();
            assertThat(lower, containsString("unknown"));
            assertThat(lower, containsString(givenAddress));
        }
    }


    @Parameters({
            "192.168.0.100:6543",
            "8.8.8.8:8",
    })
    @Test
    public void parseAddressWifiGood(String givenIpAddress) throws Exception {
        // setup

        // execute
        Connector actualConnector = AddressParser.parseDeviceAddress(givenIpAddress);

        // verify
        assertThat(actualConnector.getClass(), is(equalTo(ClientSocketConnector.class)));
    }

    @Parameters({
            "8.8.8.8",
            "192.168.0.500:6543",
            "192.168.0.5x0:6543",
    })
    @Test
    public void parseAddressWifiBad(String givenIpAddress) throws Exception {
        // setup

        // execute
        try {
            Connector actualConnector = AddressParser.parseDeviceAddress(givenIpAddress);
            Assert.fail("Expected IP address to fail:" + givenIpAddress);
        } catch (IOException | CommandLineUsageException ignore) {

        }
    }

    @Test
    public void parseAddressSerial() throws Exception {
        // setup
        String givenSerial;
        if (SystemUtils.IS_OS_UNIX) {
            givenSerial = "/dev/rfcomm0";
        } else {
            givenSerial = "COM5";
        }

        // execute
        try {
            Connector actualConnector = AddressParser.parseDeviceAddress(givenSerial);
            Assert.fail("Currently unimplemented");
        } catch (NotImplementedException ignore) {

        }

        // verify
        // assertThat(actualConnector.getClass(), is(equalTo(...Connector.class)));
    }

}

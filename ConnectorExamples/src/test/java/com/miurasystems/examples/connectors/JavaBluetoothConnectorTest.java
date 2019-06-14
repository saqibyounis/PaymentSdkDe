package com.miurasystems.examples.connectors;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import javax.bluetooth.RemoteDevice;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Requires Bluetooth to work on your computer
 * And for your BT device to know the address/names of specific devices.
 * Not the greatest test in the world...
 */
// todo Add DiscoveryAgent as dependency so we can mock it and therefore test this class

@RunWith(JUnitParamsRunner.class)
public class JavaBluetoothConnectorTest {

    @Ignore("Requires live BT stack with cached address")
    @Parameters({
            "Miura BT-Prodserver, true",
            "Miura 515, true",
            "Miura 999, false",
            "This device name is definitely made up, false",
            "515, false",
            "Miura, false",
    })
    @Test
    public void findDeviceForName(String givenDeviceName, boolean expectedResult)
            throws IOException {
        // setup

        // execute
        RemoteDevice device = JavaBluetoothConnector.findDeviceForName(givenDeviceName);
        boolean actualResult = device != null;

        // verify
        assertThat(actualResult, is(equalTo(expectedResult)));
    }

    @Ignore("Requires live BT stack with cached address")
    @Parameters({
            "20121115165D, true", // Miura BT-Prodserver
            "20121115165d, true", // Miura BT-Prodserver
            "20:12:11:15:16:5D, true", // Miura BT-Prodserver
            "20:12:11:15:16:5d, true", // Miura BT-Prodserver
            "20:12111516:5D, true", // stupid, but works
            "A0:B1:C2:D3:E4:F5, false",
    })
    @Test
    public void findDeviceForAddress(String givenAddress, boolean expectedResult)
            throws IOException {
        // setup

        // execute
        RemoteDevice device = JavaBluetoothConnector.findDeviceForAddress(givenAddress);
        boolean actualResult = device != null;

        // verify
        assertThat(actualResult, is(equalTo(expectedResult)));
    }

    @Ignore("Requires live BT stack")
    @Parameters({
            "Miura BT-Prodserver",
            "c0:cb:38:fe:d4:",
    })
    @Test
    public void invalidBTAddresses(String givenAddress) {
        // setup

        // execute
        try {
            JavaBluetoothConnector.findDeviceForAddress(givenAddress);
            Assert.fail("Expected findDeviceForAddress to fail on these addresses");
        } catch (IOException e) {
            String lower = e.getMessage().toLowerCase();
            assertThat(lower, containsString("invalid"));
            assertThat(lower, containsString("address"));
        }
    }

    @Ignore("Requires live BT stack")
    @Test
    public void getAllDevicesString() throws IOException {
        String s = JavaBluetoothConnector.getAllDevicesString();
        System.out.println(s);
        assertThat(s, is(notNullValue()));
    }
}

/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.api.executor;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import android.support.annotation.NonNull;

import com.miurasystems.miuralibrary.MpiClient;
import com.miurasystems.miuralibrary.api.listener.ApiGetSoftwareInfoListener;
import com.miurasystems.miuralibrary.api.objects.SoftwareInfo;
import com.miurasystems.examples.connectors.JavaBluetoothConnector;
import com.miurasystems.miuralibrary.comms.Connector;

import org.junit.Ignore;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.bluetooth.RemoteDevice;

public class MiuraManagerTest {

    /* Live test. Requires PED. */
    @Ignore
    @Test
    public void closeFromAsyncThread() throws Exception {

        final CountDownLatch latch = new CountDownLatch(1);

        RemoteDevice device = JavaBluetoothConnector.findDeviceForName("Miura 515");
        assert device != null;
        String url = JavaBluetoothConnector.getUrlForDevice(device);
        Connector connector = new JavaBluetoothConnector(url);

        final MiuraManager mm = MiuraManager.getInstance();

        mm.setConnector(connector);

        mm.openSession();
        mm.getSoftwareInfo(new ApiGetSoftwareInfoListener() {
            @Override
            public void onSuccess(SoftwareInfo softwareInfo) {
                mm.closeSession();
                latch.countDown();
            }

            @Override
            public void onError() {
                mm.closeSession();
                latch.countDown();
            }
        });

        boolean ok = latch.await(5L, TimeUnit.SECONDS);
        assertThat(ok, is(true));
    }

    @Ignore
    @Test
    public void closeFromExecuteAsyncThread() throws Exception {

        final CountDownLatch latch = new CountDownLatch(1);

        RemoteDevice device = JavaBluetoothConnector.findDeviceForName("Miura 515");
        assert device != null;
        String url = JavaBluetoothConnector.getUrlForDevice(device);
        Connector connector = new JavaBluetoothConnector(url);

        final MiuraManager mm = MiuraManager.getInstance();

        mm.setConnector(connector);

        mm.openSession();
        mm.executeAsync(new MiuraManager.AsyncRunnable() {
            @Override
            public void runOnAsyncThread(@NonNull MpiClient client) {
                mm.closeSession();
                latch.countDown();
            }
        });

        boolean ok = latch.await(5L, TimeUnit.SECONDS);
        assertThat(ok, is(true));
    }
}

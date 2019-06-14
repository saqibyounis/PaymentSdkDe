/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.examples.rki;


import static com.miurasystems.miuralibrary.enums.InterfaceType.MPI;
import static com.miurasystems.miuralibrary.tlv.BinaryUtil.parseHexBinary;

import android.support.annotation.NonNull;

import com.miurasystems.miuralibrary.MpiClient;
import com.miurasystems.miuralibrary.api.executor.MiuraManager;
import com.miurasystems.miuralibrary.api.objects.P2PEStatus;
import com.miurasystems.miuralibrary.api.utils.DisplayTextUtils;
import com.miurasystems.miuralibrary.api.utils.GetDeviceFile;
import com.miurasystems.miuralibrary.enums.RKIError;
import com.miurasystems.miuralibrary.enums.SelectFileMode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

class Tuple<typeA, typeB> {

    public final typeA A;

    public final typeB B;

    public Tuple(typeA a, typeB b) {
        A = a;
        B = b;
    }
}

class P2peException extends IOException {

    private static final long serialVersionUID = 3933749769814279065L;

    public P2peException(String message) {
        super(message);
    }

    public P2peException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public String getMessage() {
        //noinspection ConstantConditions
        return super.getMessage();
    }
}

public final class MiuraRKIManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(MiuraRKIManager.class);
    private static final Charset UTF_8 = Charset.forName("utf-8");

    private MiuraRKIManager() {
    }

    private static HostRkiCommand p2peReadInitFiles(MpiClient client) throws P2peException {
        List<String> filenames = Arrays.asList(
            "prod-sign.crt",
            "terminal.crt",
            "temp-keyload.crt",
            "suggested-iksn.txt"
        );
        HashMap<String, String> map = new HashMap<>(4);

        for (String filename : filenames) {
            byte[] bytes = GetDeviceFile.getDeviceFile(client, MPI, filename, null);
            if (bytes == null) {
                throw new P2peException("Download " + filename + " certificate failed");
            }
            String data = new String(bytes, UTF_8);
            map.put(filename, data);
        }

        //noinspection ConstantConditions
        return new HostRkiCommand(
            map.get("prod-sign.crt"),
            map.get("terminal.crt"),
            map.get("temp-keyload.crt"),
            map.get("suggested-iksn.txt")
        );
    }

    private static HostRkiResponse requestKeysFromHost(
        MpiClient client,
        HostRkiCommand hostRkiCommand
    ) throws P2peException {
        boolean ok = client.displayText(MPI,
            DisplayTextUtils.getCenteredText("Connecting \n to key host."),
            true, true, true
        );
        if (!ok) {
            throw new P2peException("Display text failed");
        }

        try {
            return RestRkiManager.postRkiInitRequest(hostRkiCommand);
        } catch (TestHostException e) {
            throw new P2peException("No valid response from host", e);
        }
    }

    private static void injectKeysToPed(
        final MpiClient mpiClient,
        final HostRkiResponse hostResponse
    ) throws P2peException {
        boolean ok = mpiClient.displayText(
            MPI,
            DisplayTextUtils.getCenteredText("Keys Received\ninjecting into PED."),
            true, true, true
        );
        if (!ok) {
            throw new P2peException("Display Text failed");
        }

        List<Tuple<byte[], String>> list = Arrays.asList(
            new Tuple<>(hostResponse.HsmCert.getBytes(UTF_8), "HSM.crt"),
            new Tuple<>(parseHexBinary(hostResponse.Kbpk), "kbpk-0001.rsa"),
            new Tuple<>(parseHexBinary(hostResponse.KbpkSig), "kbpk-0001.rsa.sig"),
            new Tuple<>(hostResponse.SredIksn.getBytes(UTF_8), "dukpt-sred-iksn-0001.txt"),
            new Tuple<>(hostResponse.SredTr31.getBytes(UTF_8), "dukpt-sred-0001.tr31"),
            new Tuple<>(hostResponse.PinIksn.getBytes(UTF_8), "dukpt-pin-iksn-0001.txt"),
            new Tuple<>(hostResponse.PinTr31.getBytes(UTF_8), "dukpt-pin-0001.tr31")
        );
        for (Tuple<byte[], String> pair : list) {
            byte[] bytes = pair.A;
            String fileName = pair.B;
            ok = uploadBinary(mpiClient, bytes, fileName);
            if (!ok) {
                String err = String.format("Upload of '%s' failed", fileName);
                throw new P2peException(err);
            }
        }

        RKIError rkiError = mpiClient.p2peImport(MPI);
        if (rkiError != RKIError.NoError) {
            throw new P2peException("Key Injection Failed");
        }
    }

    private static boolean uploadBinary(
        MpiClient client,
        byte[] data,
        String fileName
    ) {
        int pedFileSize = client.selectFile(MPI, SelectFileMode.Truncate, fileName);
        //noinspection SimplifiableIfStatement
        if (pedFileSize < 0) {
            return false;
        }
        return client.streamBinary(MPI, false, data, 0, data.length, 100);
    }

    private static void p2peInitialise(MpiClient client) throws P2peException {
        String centeredText = DisplayTextUtils.getCenteredText("Preparing for\nKey injection...");
        boolean ok = client.displayText(MPI, centeredText, true, true, true);
        if (!ok) {
            throw new P2peException("Display failed");
        }

        /*Request to do key injection*/
        ok = client.p2peInitialise(MPI);
        if (!ok) {
            throw new P2peException("Init failed");
        }

        P2PEStatus p2PEStatus = client.p2peStatus(MPI);
        if (p2PEStatus == null || !p2PEStatus.isInitialised) {
            throw new P2peException("P2PE initialised bit isn't set?");
        }

        LOGGER.trace("PED initialised OK for P2PE");
    }

    private static void confirmP2peWorks(MpiClient client) throws P2peException {
        P2PEStatus p2PEStatus = client.p2peStatus(MPI);
        if (p2PEStatus == null) {
            throw new P2peException("Failed to get P2PE status byte");
        } else if (p2PEStatus.isInitialised) {
            throw new P2peException("P2PE initialised bit is set after injection?");
        }

        boolean anyKeyReady = p2PEStatus.isPINReady || p2PEStatus.isSREDReady;
        if (!anyKeyReady) {
            throw new P2peException("No keys ready after injection?");
        }

        boolean ok = client.displayText(
            MPI,
            DisplayTextUtils.getCenteredText("Key injected ok!"),
            true, true, true);
        if (!ok) {
            throw new P2peException("Display for Key Injection Failed");
        }
    }

    public static void injectKeys(MpiClient client) throws P2peException {
        LOGGER.trace("Injecting keys...");

        p2peInitialise(client);
        HostRkiCommand hostCommand = p2peReadInitFiles(client);
        HostRkiResponse hostResponse = requestKeysFromHost(
                client,
                hostCommand
        );
        injectKeysToPed(client, hostResponse);
        confirmP2peWorks(client);

        LOGGER.trace("...key injection success!");
    }

    public static void injectKeysAsync(final MiuraRKIListener listener) {

        MiuraManager.getInstance().executeAsync(new MiuraManager.AsyncRunnable() {
            @Override
            public void runOnAsyncThread(@NonNull MpiClient client) {
                try {
                    injectKeys(client);
                    listener.onSuccess();
                } catch (P2peException e) {
                    LOGGER.trace("P2peException, calling onError: ", e);
                    listener.onError(e.getMessage());
                } catch (Throwable e) {
                    LOGGER.trace("Unchecked exception?? calling onError: ", e);
                    listener.onError(e.getMessage());
                }
            }
        });
    }
}


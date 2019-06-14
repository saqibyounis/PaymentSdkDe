/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.sampleapps.commandline;

import com.miurasystems.examples.rki.MiuraRKIManager;
import com.miurasystems.miuralibrary.MpiClient;
import com.miurasystems.miuralibrary.comms.Connector;
import com.miurasystems.miuralibrary.enums.InterfaceType;
import com.miurasystems.miuralibrary.enums.ResetDeviceType;
import com.miurasystems.miuralibrary.events.MpiEvents;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public final class Main {

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    /*
        CommandLineSdkApp <args> <device>

        <args>:
            d, demo
            p, ped_shop
            i, interactive on local jvm
            t, integration_tests

        <device>
            bt name
            bt mac address
            ip
            usb
            internal socket, for m20?

        If no option given on command-line, do:
            1. Run a simple demo.
            2. Command line UI transaction
            3. Ped-UI transaction
            4. Run integration tests
            5. ???
     */

    private Main() {
    }

    public static void main(String[] args) throws IOException {

        LOGGER.info("CommandLineSdkApp started! args:{}", StringUtils.join(args, ","));

        CommandLineArguments cmdLine;
        try {
            cmdLine = CommandLineArguments.parseCommandLine(args);
        } catch (CommandLineUsageException exception) {
            String usage = CommandLineArguments.getUsage();
            userPrintln("Error: " + exception.getMessage());
            userPrintln("\n");
            userPrintln(usage);

            System.exit(1);
            return;
        }

        LOGGER.trace("Command line parsed: {}", cmdLine);

        switch (cmdLine.getProgramMode()) {
            case Help:
                String usage = CommandLineArguments.getUsage();
                userPrintln(usage);
                break;
            case Version:
                userPrintln("CommandLineSdkApp version 1.0"); //??
                break;
            case Demo:
                runDemo(cmdLine);
                break;
            case Interactive:
                InteractiveTransactionDemo.runInteractive(cmdLine);
                break;
            case PedShop:
                Connector connector = cmdLine.getConnector();
                if (connector == null) {
                    throw new AssertionError("Connector expected?");
                }
                PedShopDemo.runInteractive(connector);
                break;
        }

        System.exit(0);
    }

    private static void runDemo(CommandLineArguments cmdLine) throws IOException {

        Connector connector = cmdLine.getConnector();
        if (connector == null) {
            throw new AssertionError("Connector expected?");
        }

        MpiEvents events = new MpiEvents();
        MpiClient client = new MpiClient(connector, events);

        client.openSession();
        client.resetDevice(InterfaceType.MPI, ResetDeviceType.Soft_Reset);
        client.displayText(InterfaceType.MPI, "Hello World!", false, false, true);
        try {
            Thread.sleep(2500L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        client.closeSession();
    }

    private static void userPrintln(String text, Object... vargs) {
        System.out.println(String.format(text, vargs));
    }

    private static void userPrint(String text, Object... vargs) {
        System.out.print(String.format(text, vargs));
    }

}

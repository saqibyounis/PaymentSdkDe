/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.sampleapps.commandline;


import com.miurasystems.miuralibrary.comms.Connector;

import org.slf4j.LoggerFactory;

import java.io.IOException;

import javax.annotation.Nullable;

class CommandLineArguments {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(
            CommandLineArguments.class);

    private final ProgramMode mProgramMode;

    @Nullable
    private final Connector mConnector;

    @Nullable
    private final String mAddress;


    private CommandLineArguments(ProgramMode programMode) {
        mProgramMode = programMode;
        mConnector = null;
        mAddress = null;
    }

    private CommandLineArguments(
            ProgramMode programMode,
            Connector connector,
            String address
    ) {
        mProgramMode = programMode;
        mConnector = connector;
        mAddress = address;
    }

    public ProgramMode getProgramMode() {
        return mProgramMode;
    }

    @Nullable
    public String getAddress() {
        return mAddress;
    }

    @Nullable
    public Connector getConnector() {
        return mConnector;
    }

    @Override
    public String toString() {
        if (mConnector == null) {
            return String.format("CommandLineArguments(%s)", mProgramMode);
        } else {
            return String.format(
                    "CommandLineArguments(%s, %s, %s)",
                    mProgramMode, mConnector, mAddress
            );
        }
    }

    static CommandLineArguments parseCommandLine(String[] args)
            throws CommandLineUsageException {

        if (args.length < 1) {
            throw new CommandLineUsageException("No mode provided");
        }

        ProgramMode mode;
        try {
            mode = ProgramMode.parseCommandLine(args[0]);
        } catch (ProgramModeParseException e) {
            //noinspection ThrowInsideCatchBlockWhichIgnoresCaughtException
            throw new CommandLineUsageException(e.getMessage());
        }

        if (mode == ProgramMode.Help || mode == ProgramMode.Version) {
            return new CommandLineArguments(mode);
        }

        if (args.length < 2) {
            throw new CommandLineUsageException("No address given");
        } else if (args.length > 2) {
            throw new CommandLineUsageException("Too many parameters");
        }

        String givenAddress = args[1].trim();

        Connector connector;
        try {
            connector = AddressParser.parseDeviceAddress(givenAddress);
        } catch (IOException e) {
            throw new CommandLineUsageException("Problem parsing address", e);
        }
        return new CommandLineArguments(mode, connector, givenAddress);
    }

    static String getUsage() {

        StringBuilder builder = new StringBuilder(256);
        String modeColFormat = "%7s |  %-20s | %s\n";

        // Java doesn't have exe name in args[0], so just hardcode it
        builder.append("Usage: CommandLineSdkApp <mode> <address>\n");
        builder.append("\n");
        builder.append("Application that demonstrates usage of the Miura Java SDK\n");
        builder.append("\n");
        builder.append("Valid <mode>:\n");
        builder.append("\n");
        ProgramMode.getUsage(builder);
        builder.append("\n");
        builder.append("Valid <address> formats:\n");
        builder.append("\n");
        AddressParser.getUsage(builder);

        return builder.toString();
    }
}

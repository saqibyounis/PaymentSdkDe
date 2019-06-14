/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.sampleapps.commandline;

enum ProgramMode {

    Demo("d", "demo",
            "Run a short demo on the device"
    ),
    PedShop("p", "ped_shop",
            "Run an interactive transaction on the device"
    ),
    Interactive("i", "interactive",
            "Run an interactive transaction on this computer"
    ),
    Help("h", "help", "Show usage help", true),
    Version("v", "version", "Show program version", true);

    private final String mShortStr;
    private final String mLongStr;
    private final String mDescription;
    private final boolean mAcceptGnuStyle;

    ProgramMode(String shortStr, String longStr, String description) {
        this(shortStr, longStr, description, false);
    }

    ProgramMode(String shortStr, String longStr, String description, boolean acceptGnuStyle) {
        mShortStr = shortStr;
        mLongStr = longStr;
        mDescription = description;
        mAcceptGnuStyle = acceptGnuStyle;
    }

    static void getUsage(StringBuilder builder) {
        String modeColFormat = "    %7s |  %-20s | %s\n";
        builder.append(String.format(modeColFormat, "short", "long", "description"));
        builder.append("      -------------------------------------------\n");
        for (ProgramMode mode : ProgramMode.values()) {
            String line = String.format(
                    modeColFormat,
                    mode.mShortStr,
                    mode.mLongStr,
                    mode.mDescription);
            builder.append(line);
        }
    }

    static ProgramMode parseCommandLine(String arg) throws ProgramModeParseException {
        // Would be named 'valueOf', but can't override valueOf(String) in Java

        String trimmed = arg.trim();
        if (trimmed.isEmpty()) {
            throw new ProgramModeParseException("empty string?");
        }

        for (ProgramMode mode : ProgramMode.values()) {

            /* special case for standard GNU style help/version */
            if (mode.mAcceptGnuStyle) {
                String gnuLong = "--" + mode.mLongStr;
                String gnuShort = '-' + mode.mShortStr;
                if (gnuLong.equals(arg) || gnuShort.equals(arg)) {
                    return mode;
                }
            }

            if (mode.mShortStr.equals(trimmed) || mode.mLongStr.equals(trimmed)) {
                return mode;
            }
        }

        throw new ProgramModeParseException("Unknown ProgramMode: " + trimmed);
    }
}

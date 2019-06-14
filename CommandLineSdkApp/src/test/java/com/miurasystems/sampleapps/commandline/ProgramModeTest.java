/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.sampleapps.commandline;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

@RunWith(JUnitParamsRunner.class)
public class ProgramModeTest {


    @Test
    public void parseCommandLine_emptyString() {
        // setup
        String arg = "";

        // execute
        try {
            ProgramMode.parseCommandLine(arg);
            Assert.fail("Expected parseCommandLine to throw exception");
        } catch (ProgramModeParseException e) {
            // verify
            assertThat(e.getMessage().toLowerCase(), containsString("empty"));
        }
    }

    @Test
    public void parseCommandLine_notAMode() {
        // setup
        String arg = "gibberish";

        // execute
        try {
            ProgramMode.parseCommandLine(arg);
            Assert.fail("Expected parseCommandLine to throw exception");
        } catch (ProgramModeParseException e) {
            // verify
            assertThat(e.getMessage().toLowerCase(), containsString("unknown"));
        }
    }

    @Test
    @Parameters({
            "demo, true, Demo",
            "DEMO, false, Demo", // 3rd param here ignored, but must be valid for JUnitParams
            "ped_shop, true, PedShop",
            "PedShop, false, Demo", // 3rd param here ignored
            "interactive, true, Interactive",
            // "integration_tests, true, Tests",
            "tests, false, Demo", // 3rd param here ignored
    })
    public void parseCommandLine_validLongModes(
            String givenModeString,
            boolean expectedValid,
            ProgramMode expectedMode
    ) throws ProgramModeParseException {
        // setup

        try {
            ProgramMode mode = ProgramMode.parseCommandLine(givenModeString);
            if (expectedValid) {
                assertThat(mode, is(equalTo(expectedMode)));
            } else {
                Assert.fail("Expected parseCommandLine to throw exception for " + givenModeString);
            }
        } catch (ProgramModeParseException ignore) {
            if (expectedValid) {
                Assert.fail("Unexpected exception for " + givenModeString);
            }
        }
    }


    @Test
    @Parameters({
            "d, true, Demo",
            "D, false, Demo", // 3rd param here ignored, but must be valid for JUnitParams
            "p, true, PedShop",
            "P, false, Demo", // 3rd param here ignored
            "i, true, Interactive",
            // "t, true, Tests",
    })
    public void parseCommandLine_validShortModes(
            String givenModeString,
            boolean expectedValid,
            ProgramMode expectedMode
    ) throws ProgramModeParseException {
        // setup

        try {
            ProgramMode mode = ProgramMode.parseCommandLine(givenModeString);
            if (expectedValid) {
                assertThat(mode, is(equalTo(expectedMode)));
            } else {
                Assert.fail("Expected parseCommandLine to throw exception for " + givenModeString);
            }
        } catch (ProgramModeParseException ignore) {
            if (expectedValid) {
                Assert.fail("Unexpected exception for " + givenModeString);
            }
        }
    }

    @Test
    public void parseCommandLine_validSubstringInInvalidText() {
        // setup
        String arg = "demoman";

        // execute
        try {
            ProgramMode.parseCommandLine(arg);
            Assert.fail("Expected parseCommandLine to throw exception");
        } catch (ProgramModeParseException e) {
            // verify
            assertThat(e.getMessage().toLowerCase(), containsString("unknown"));
        }
    }


    @Test
    public void parseCommandLine_substringGivenOnCommandLine() {
        // setup
        String arg = "dem";

        // execute
        try {
            ProgramMode.parseCommandLine(arg);
            Assert.fail("Expected parseCommandLine to throw exception");
        } catch (ProgramModeParseException e) {
            // verify
            assertThat(e.getMessage().toLowerCase(), containsString("unknown"));
        }
    }

}

/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.sampleapps.commandline;

import static com.miurasystems.examples.transactions.EmvTransactionType.Chip;
import static com.miurasystems.examples.transactions.EmvTransactionType.Contactless;
import static com.miurasystems.miuralibrary.enums.InterfaceType.MPI;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.miurasystems.examples.transactions.EmvChipInsertStatus;
import com.miurasystems.examples.transactions.EmvTransaction;
import com.miurasystems.examples.transactions.EmvTransactionException;
import com.miurasystems.examples.transactions.EmvTransactionSummary;
import com.miurasystems.examples.transactions.EmvTransactionType;
import com.miurasystems.examples.transactions.MagSwipeError;
import com.miurasystems.examples.transactions.MagSwipePinResult;
import com.miurasystems.examples.transactions.MagSwipeSummary;
import com.miurasystems.examples.transactions.MagSwipeTransaction;
import com.miurasystems.examples.transactions.MagSwipeTransactionException;
import com.miurasystems.examples.transactions.PaymentMagType;
import com.miurasystems.examples.transactions.UserInputType;
import com.miurasystems.miuralibrary.MpiClient;
import com.miurasystems.miuralibrary.MpiClient.GetNumericDataError;
import com.miurasystems.miuralibrary.Result;
import com.miurasystems.miuralibrary.comms.Connector;
import com.miurasystems.miuralibrary.enums.BacklightSettings;
import com.miurasystems.miuralibrary.enums.StatusSettings;
import com.miurasystems.miuralibrary.events.ConnectionInfo;
import com.miurasystems.miuralibrary.events.MpiEventHandler;
import com.miurasystems.miuralibrary.events.MpiEvents;
import com.miurasystems.miuralibrary.tlv.CardData;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

// todo SDK should probably have this, rather than an INTEGER
enum PedKeyPress {
    CancelKey, AcceptKey, BackKey, MaskedNumericKey, BluetoothKey;

    private static final Logger LOGGER = LoggerFactory.getLogger(PedKeyPress.class);

    public static PedKeyPress valueOf(int value) {

        switch (value) {
            case 0:
                // 0, '\0'
                // This is actually "power button or numeric key"...
                return PedKeyPress.MaskedNumericKey;
            case 0x1b:
                // 27, ESC, '\e'
                return PedKeyPress.CancelKey;
            case 0x82:
                // 130
                return PedKeyPress.BackKey;
            case 0x81:
                // 129
                return PedKeyPress.BluetoothKey;
            case 0xd:
                // 13, '\r'
                return PedKeyPress.AcceptKey;
            default:
                // Just return a redacted key press. That way the app still knows something
                // was pressed, just not what, which is the same as MaskedNumericKey.
                LOGGER.warn("Unknown PedKeyPress: {}", value);
                return MaskedNumericKey;

        }
    }
}

final class PedShopDemo {

    private static final Logger LOGGER = LoggerFactory.getLogger(PedShopDemo.class);
    private static final int GBP = 826;

    private PedShopDemo() {
    }

    static void runInteractive(Connector connector) {

        MpiEvents events = new MpiEvents();
        MpiClient client = new MpiClient(connector, events);

        Thread mainThread = Thread.currentThread();
        BlockingQueue<Integer> keyPresses = new LinkedBlockingQueue<>();
        MpiEventHandler<Integer> keyPressHandler = boxedInt -> {
            keyPressEventHandler(mainThread, keyPresses, boxedInt);
        };
        MpiEventHandler<ConnectionInfo> disconnectHandler = status -> {
            disconnectionEventHandler(mainThread);
        };

        LOGGER.debug("Connecting to {}", connector);
        events.Disconnected.register(disconnectHandler);
        try {
            client.openSession();
        } catch (IOException e) {
            LOGGER.info("Failed to connect to device. Cancelling transaction", e);
            return;
        }

        events.KeyPressed.register(keyPressHandler);
        client.keyboardStatus(MPI, StatusSettings.Enable, BacklightSettings.NoChange);

        try {
            int total = getTotalFromPOS(client, keyPresses);
            if (total < 0) {
                displayAutoAdvancedScreen(
                        client, keyPresses,
                        "Transaction cancelled",
                        ContinueType.AnyKey,
                        25L
                );
                return;
            }

            boolean success = performTransaction(client, events, mainThread, keyPresses, total);
            if (!success) {
                displayAutoAdvancedScreen(
                        client, keyPresses,
                        "Transaction failed!",
                        ContinueType.AnyKey,
                        25L
                );
            }

        } catch (IOException exception) {
            LOGGER.warn("IOException hit:", exception);
        } finally {
            events.Disconnected.deregister(disconnectHandler);

            client.keyboardStatus(MPI, StatusSettings.Disable, BacklightSettings.NoChange);
            events.KeyPressed.deregister(keyPressHandler);
            LOGGER.debug("Closing connection to {}", connector);
            client.closeSession();
        }
    }

    private static boolean performTransaction(
            MpiClient mpiClient,
            MpiEvents events,
            Thread mainThread,
            BlockingQueue<Integer> keyPresses,
            int total
    ) throws IOException {
        // todo need to monitor device status
        // Todo perform standard chapter 4.3 checks
        // todo need to deregister card event handler

        userPrintln(mpiClient, "Pay by swipe, contactless, or insert card");

        // Enable card events
        BlockingQueue<CardData> cardStatus = new LinkedBlockingQueue<>();
        events.CardStatusChanged.register(cardData -> {
            cardDataEventHandler(mainThread, cardStatus, cardData);
        });
        mpiClient.cardStatus(MPI, true, false, true, true, true);

        TryContactlessResult contactlessResult = tryContactlessTransaction(
                mpiClient, keyPresses, total
        );

        long timeoutMs = 15L;
        switch (contactlessResult) {
            case ContactlessSuccess:
                return true;
            case ContactlessError:
                return false;
            case CardSwiped:
                userPrintln(mpiClient, "Card swiped.");
                break;
            case CardInserted:
                userPrintln(mpiClient, "Card inserted.");
                break;
            case TrySwipeOrInsert:
                timeoutMs = 60000L;
                userPrintln(mpiClient, ""
                        + "Contactless cancelled\n"
                        + "Try inserting\n"
                        + "  or swiping card.");
                break;
        }

        CardData cardData = waitForCardDataEvent(cardStatus, timeoutMs, keyPresses);
        if (cardData == null) {
            displayAutoAdvancedScreen(
                    mpiClient, keyPresses,
                    "Transaction timed out",
                    ContinueType.AnyKey, 15L
            );
            return false;
        }
        mpiClient.cardStatus(MPI, false, false, false, false, false);

        EmvChipInsertStatus insertStatus = EmvTransaction.canProcessEmvChip(cardData);
        if (insertStatus != EmvChipInsertStatus.NoCardPresentError) {
            return performChipTransaction(mpiClient, keyPresses, total, insertStatus);
        }

        Result<MagSwipeSummary, MagSwipeError> swipeResult =
                MagSwipeTransaction.canProcessMagSwipe(cardData);
        if (swipeResult.isSuccess()) {
            return performSwipeTransaction(
                    mpiClient, keyPresses, total,
                    swipeResult.asSuccess().getValue()
            );
        } else {
            LOGGER.warn(
                    "Received cardData that is not magswipe or card insert?:\n{}",
                    cardData
            );
            showContinuableScreen(
                    mpiClient, keyPresses,
                    "Transaction error. Transaction Cancelled",
                    ContinueType.AnyKey
            );
            return false;
        }
    }

    private static void keyPressEventHandler(
            Thread mainThread,
            BlockingQueue<Integer> keyPresses,
            Integer boxedInt
    ) {
        if (!keyPresses.offer(boxedInt)) {
            // risky print on another thread!
            LOGGER.warn("keyPresses.offer() failed!");
            mainThread.interrupt();
        }
    }

    private static void cardDataEventHandler(
            Thread mainThread,
            BlockingQueue<CardData> cardStatus,
            CardData cardData
    ) {
        LOGGER.debug("CardStatusChanged:{}", cardData);

        boolean cardPresent = false;
        EmvChipInsertStatus cardInserted = EmvTransaction.canProcessEmvChip(cardData);
        switch (cardInserted) {
            case NoCardPresentError:
                // It wasn't an insert... was it a swipe?
                Result<MagSwipeSummary, MagSwipeError> swiped =
                        MagSwipeTransaction.canProcessMagSwipe(cardData);
                cardPresent = swiped.isSuccess();
                break;
            case CardInsertedOk:
            case CardIncompatibleError:
                cardPresent = true;
                break;
        }

        if (cardPresent) {
            if (!cardStatus.offer(cardData)) {
                // risky print on another thread!
                LOGGER.warn("cardStatus.offer() failed!");
                mainThread.interrupt();
            }
        }
    }

    private static void disconnectionEventHandler(Thread mainThread) {
        LOGGER.warn("Unexpected disconnection!");
        // interrupt main thread incase it's waiting for keyPresses
        mainThread.interrupt();
    }

    private static TryContactlessResult tryContactlessTransaction(
            MpiClient mpiClient,
            BlockingQueue<Integer> keyPresses,
            int total
    ) throws IOException {
        /*
            Prime contactless transaction.
            It will be aborted by the PED if a card is swiped/inserted
        */
        EmvTransaction ctlsTransaction = new EmvTransaction(mpiClient, Contactless);

        try {
            EmvTransactionSummary ctlsResult = ctlsTransaction.startTransaction(total, GBP);
            mpiClient.cardStatus(MPI, false, false, false, false, false);
            printEmvSuccess(mpiClient, keyPresses, ctlsResult);
            return TryContactlessResult.ContactlessSuccess;
        } catch (EmvTransactionException ctlsError) {
            LOGGER.debug("tryContactlessTransaction: catch", ctlsError);
            ctlsTransaction.abortTransaction();
            LOGGER.debug("ctlsTransaction: aborted");

            switch (ctlsError.mErrCode) {
                case CONTACTLESS_ABORT_BY_SWIPE:
                    return TryContactlessResult.CardSwiped;
                case CONTACTLESS_ABORT_BY_CARD_INSERT:
                    return TryContactlessResult.CardInserted;
                case CONTACTLESS_INSERT_OR_SWIPE:
                case CONTACTLESS_INSERT_CARD:
                case CONTACTLESS_INSERT_SWIPE_OR_OTHER_CARD:
                    return TryContactlessResult.TrySwipeOrInsert;
                case USER_CANCELLED:
                    return TryContactlessResult.ContactlessError;
                case CONTACTLESS_TIMEOUT:
                default:
                    printEmvError(mpiClient, keyPresses, ctlsError, Contactless);
                    return TryContactlessResult.ContactlessError;
            }
        }
    }

    private static boolean performSwipeTransaction(
            MpiClient mpiClient,
            BlockingQueue<Integer> keyPresses, int total,
            MagSwipeSummary magSwipeSummary
    ) throws IOException {
        MagSwipeTransaction swipeTransaction = new MagSwipeTransaction(mpiClient);

        UserInputType userInputType = UserInputType.resolvePaymentType(
                PaymentMagType.Pin, magSwipeSummary
        );
        if (userInputType == UserInputType.Signature) {
            showContinuableScreen(
                    mpiClient, keyPresses,
                    "Card Rejected\nCard doesn't\n  support PIN",
                    ContinueType.AnyKey
            );
            return false;
        }

        // fixme the MagSwipeTransaction object messes with the screen text...

        userPrintln(mpiClient, "Card swiped.\nProcessing\n  transaction.");
        try {
            MagSwipePinResult pinResult = swipeTransaction.processPinTransaction(
                    magSwipeSummary, total, GBP
            );
            displayAutoAdvancedScreen(
                    mpiClient,
                    keyPresses,
                    "Card swiped.\nPIN OK\nTransaction complete.",
                    ContinueType.AnyKey, 15L
            );
            return true;
        } catch (MagSwipeTransactionException swipeException) {
            showContinuableScreen(
                    mpiClient, keyPresses,
                    String.format(
                            "Swipe error\n%s\n%s",
                            swipeException.mErrorCode,
                            swipeException.getMessage()
                    ),
                    ContinueType.AnyKey
            );
            return false;
        }
    }

    private static boolean performChipTransaction(
            MpiClient mpiClient,
            BlockingQueue<Integer> keyPresses,
            int total,
            EmvChipInsertStatus insertStatus
    ) throws IOException {
        if (insertStatus == EmvChipInsertStatus.CardIncompatibleError) {
            showContinuableScreen(
                    mpiClient, keyPresses,
                    "Card incompatible.\nTransaction cancelled",
                    ContinueType.AnyKey
            );
            return false;
        } else if (insertStatus != EmvChipInsertStatus.CardInsertedOk) {
            throw new AssertionError();
        }

        EmvTransaction emvTransaction = new EmvTransaction(mpiClient, Chip);

        userPrintln(mpiClient, "Card inserted.\nProcessing\n  transaction.");
        try {
            EmvTransactionSummary result = emvTransaction.startTransaction(total, GBP);
            printEmvSuccess(mpiClient, keyPresses, result);
            return true;
        } catch (EmvTransactionException exception) {
            printEmvError(mpiClient, keyPresses, exception, Chip);
            return false;
        }
    }

    private static void printEmvError(
            MpiClient mpiClient,
            BlockingQueue<Integer> keyPresses,
            EmvTransactionException emvError,
            EmvTransactionType transactionType
    ) throws IOException {
        String screen = String.format(""
                        + "%s Error:\n"
                        + "%s\n"
                        + "%s",
                transactionType,
                emvError.mErrCode,
                emvError.getMessage()
        );
        showContinuableScreen(
                mpiClient, keyPresses, screen, ContinueType.AnyKey
        );
    }

    private static void printEmvSuccess(
            MpiClient mpiClient,
            BlockingQueue<Integer> keyPresses,
            EmvTransactionSummary summary
    ) throws IOException {
        displayAutoAdvancedScreen(
                mpiClient, keyPresses, "Transaction success!", ContinueType.AnyKey, 15L
        );
    }

    @Nullable
    private static CardData waitForCardDataEvent(
            BlockingQueue<CardData> cardStatus,
            long timeoutMs,
            BlockingQueue<Integer> keyPresses) {
        // wait for card event


        long remaining = timeoutMs;
        long keyWait = 15L;
        long cardWait = 15L;
        while (remaining > 0L) {

            PedKeyPress keyPress = waitForKeyPress(keyPresses, keyWait, TimeUnit.MILLISECONDS);
            if (keyPress == PedKeyPress.CancelKey) {
                return null;
            }

            CardData cardData;
            try {
                cardData = cardStatus.poll(cardWait, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ignore) {
                LOGGER.info("waitForCardDataEvent's poll interrupted?");
                return null;
            }
            if (cardData == null) {
                remaining -= (keyWait + cardWait);
            } else {
                return cardData;
            }
        }

        LOGGER.debug("waitForCardDataEvent's poll failed.");
        return null;
    }

    /** POS as in till operator */
    private static int getTotalFromPOS(
            MpiClient mpiClient,
            BlockingQueue<Integer> keyPresses
    ) throws IOException {
        MenuChoice menuChoice = showInteractiveMenu(mpiClient, keyPresses);
        if (menuChoice == null) {
            return -1;
        }

        int quantity = getQuantityFromUser(mpiClient, keyPresses, menuChoice);
        if (quantity < 0) {
            return -1;
        }

        int tip = getTipFromUser(mpiClient);
        if (tip < 0) {
            return -1;
        }

        int total = confirmTotalWithUser(
                mpiClient, keyPresses, menuChoice, quantity, tip
        );
        return total;
    }

    @Nullable
    private static MenuChoice showInteractiveMenu(
            MpiClient mpiClient,
            BlockingQueue<Integer> keyPresses
    ) throws IOException {
        List<MenuChoice> menuChoices = Arrays.asList(
                new MenuChoice("Apples", 30),
                new MenuChoice("Mango", 105),
                new MenuChoice("Melon", 260)
        );

        StringBuilder builder = new StringBuilder(512);
        builder.append("Menu:\n");
        for (int i = 0; i < menuChoices.size(); i++) {
            MenuChoice choice = menuChoices.get(i);
            builder.append(String.format("  %d. %s\n", i + 1, choice));
        }
        String text = builder.toString();

        while (true) {
            PedKeyPress pedKeyPress = showContinuableScreen(
                    mpiClient, keyPresses, text, ContinueType.AcceptAndCancel
            );

            switch (pedKeyPress) {
                case CancelKey:
                    return null;
                case AcceptKey:
                    int digit = readUserDigit(
                            mpiClient,
                            keyPresses,
                            1, menuChoices.size(),
                            UserDigitInputType.Selection
                    );
                    if (digit >= 1) {
                        return menuChoices.get(digit - 1);
                    }
                    break;
                default:
                    String warningText = "Only press \u2714 or X\n"
                            + "  on the menu screen\n"
                            + "Item selection is \n"
                            + "  on next screen...\n";
                    displayAutoAdvancedScreen(mpiClient, keyPresses, warningText,
                            ContinueType.AnyKey, 20L
                    );
                    break;
            }
        }
    }

    private static int getQuantityFromUser(
            MpiClient mpiClient,
            BlockingQueue<Integer> keyPresses,
            MenuChoice menuChoice
    ) throws IOException {

        String format = String.format(
                "Item: %s\n" +
                        "Price: £%.2f\n" +
                        "Prepare to enter\n" +
                        "  quantity. (0-9)\n",
                menuChoice.mName,
                (float) menuChoice.mPrice / 100.0f
        );

        while (true) {
            PedKeyPress press = showContinuableScreen(
                    mpiClient, keyPresses, format,
                    ContinueType.AcceptAndCancel
            );
            switch (press) {
                case AcceptKey:
                    int quantity = readUserDigit(
                            mpiClient, keyPresses, 1, 9, UserDigitInputType.Amount
                    );
                    if (quantity >= 1) {
                        return quantity;
                    }
                    break;
                case CancelKey:
                    return -1;
                default:
                    String warningText = ""
                            // 123456789012345678901
                            + "Only press \u2714 or X\n"
                            + "  on the info screen\n"
                            + "Enter quantity\n"
                            + "  on next screen...\n";
                    displayAutoAdvancedScreen(mpiClient, keyPresses, warningText,
                            ContinueType.AnyKey, 20L
                    );
                    break;
            }
        }
    }

    private static int getTipFromUser(MpiClient mpiClient) throws IOException {
        Result<String, GetNumericDataError> numericData = mpiClient.getNumericData(
                MPI, false, true,
                47, 43, 97,
                8, 2,
                "0.00",
                null, null, null, null
        );

        if (numericData.isError()) {
            switch (numericData.asError().getError()) {
                case InternalError:
                    throw new IOException("getNumericData() failed with InternalError!");
                case UserCancelled:
                    return -1;
            }
        }

        float tipFlt = Float.parseFloat(numericData.asSuccess().getValue());
        return Math.round(tipFlt * 100.0f);
    }

    private static int confirmTotalWithUser(
            MpiClient mpiClient,
            BlockingQueue<Integer> keyPresses,
            MenuChoice menuChoice,
            int quantity,
            int tip
    ) throws IOException {
        int total = (menuChoice.mPrice * quantity) + tip;
        String totalScreen = String.format(
                "Buying %d %s\n"
                        + "  @ £%.2f each\n" +
                        "Tip £%.2f\n" +
                        "Total due: £%.2f\n",
                quantity,
                menuChoice.mName,
                (float) menuChoice.mPrice / 100.0f,
                (float) tip / 100.0f,
                (float) total / 100.0f
        );
        while (true) {
            PedKeyPress press = showContinuableScreen(
                    mpiClient, keyPresses, totalScreen,
                    ContinueType.AcceptAndCancel
            );
            switch (press) {
                case CancelKey:
                    return -1;
                case AcceptKey:
                    return total;
                default:
                    String warningText = ""
                            // 123456789012345678901
                            + "Only press \u2714 or X\n"
                            + "  on the this screen";
                    displayAutoAdvancedScreen(
                            mpiClient, keyPresses, warningText,
                            ContinueType.AnyKey,
                            15L
                    );
                    break;
            }
        }

    }

    private static int readUserDigit(
            MpiClient mpiClient,
            BlockingQueue<Integer> keyPresses,
            int minChoice,
            int maxChoice,
            UserDigitInputType type
    ) throws IOException {

        int thirdLineIndex = type.getPromptsIndex();
        Result<String, GetNumericDataError> result = mpiClient.getNumericData(
                MPI, true, true,
                0, 0, thirdLineIndex,
                1, 0,
                null,
                null, null, null, null
        );
        if (result.isError()) {
            switch (result.asError().getError()) {
                case InternalError:
                    throw new IOException("getNumericData() failed with InternalError!");
                case UserCancelled:
                    return -1;
            }
        }

        String userInputDigit = result.asSuccess().getValue();
        LOGGER.debug("userInputDigit: {}", userInputDigit);

        int digit = Integer.parseInt(userInputDigit, 10);
        if ((digit < minChoice) || (digit > maxChoice)) {
            String availableStr;
            if (type == UserDigitInputType.Selection) {
                availableStr = "in menu";
            } else {
                availableStr = "available";
            }

            String badRangeScreen = String.format(
                    "Choice %d not\n  %s!\n"
                            + "Valid range: [%d, %d]\n",
                    digit, availableStr, minChoice, maxChoice
            );
            displayAutoAdvancedScreen(mpiClient, keyPresses, badRangeScreen,
                    ContinueType.AnyKey, 3L
            );

            return -1;
        }
        return digit;
    }

    private static PedKeyPress showContinuableScreen(
            MpiClient mpiClient,
            BlockingQueue<Integer> keyPresses,
            String screenLines,
            ContinueType continueType
    ) throws IOException {
        String prefix = prepareScreen(screenLines);

        String postfix;
        switch (continueType) {
            case AnyKey:
                postfix = "Press any key...";
                break;
            case AcceptOnly:
                postfix = "Press \u2714 to continue";
                break;
            case AcceptAndCancel:
                postfix = "Press \u2714 or X";
                break;
            default:
                throw new IllegalArgumentException("Unknown ContinueType: " + continueType);
        }

        userPrintln(mpiClient, "%s%s", prefix, postfix);
        PedKeyPress pedKeyPress = waitForKeyPress(
                keyPresses, 5L, TimeUnit.MINUTES
        );
        if (pedKeyPress == null) {
            return PedKeyPress.CancelKey;
        }

        return pedKeyPress;
    }

    private static PedKeyPress displayAutoAdvancedScreen(
            MpiClient mpiClient,
            BlockingQueue<Integer> keyPresses,
            String screenLines,
            ContinueType continueType,
            long initialTimeoutSeconds
    ) throws IOException {
        String prefix = prepareScreen(screenLines);

        String postfix;
        switch (continueType) {
            case AnyKey:
                // 123456789012345678|901
                // Press key or wait |20s
                // PressAnyKeyOrWait |20s
                // Press any key ... |20s
                postfix = "Press any key ... ";
                break;
            case AcceptOnly:
                // 1234567890123456|78901
                // Press T or wait |20s
                postfix = "Press \u2714 or wait ";
                break;
            case AcceptAndCancel:
                // 123456789012345678|901
                // Press T,X or wait |20s
                postfix = "Press \u2714,X or wait ";
                break;
            default:
                throw new IllegalArgumentException("Unknown ContinueType: " + continueType);
        }

        long autoTimeout = initialTimeoutSeconds;
        while (true) {
            userPrintln(mpiClient, "%s%s%ds", prefix, postfix, autoTimeout);
            PedKeyPress pedKeyPress = waitForKeyPress(
                    keyPresses, 1L, TimeUnit.SECONDS
            );
            if (pedKeyPress == null) {
                if (autoTimeout > 0L) {
                    autoTimeout -= 1L;
                    continue;
                }
                return PedKeyPress.AcceptKey;
            }
            return pedKeyPress;
        }
    }

    @NonNull
    private static String prepareScreen(String screenLines) {
        // todo truncate, rather than assert?
        // todo the "numNewLines" assumes the lines won't be wrapped by MPI...

        String trimmedLines = screenLines.trim();
        int numNewlines = StringUtils.countMatches(trimmedLines, '\n');
        if (numNewlines >= 4) {
            throw new AssertionError("Too many lines for auto advancement!");
        }

        StringBuilder builder = new StringBuilder(trimmedLines);
        while (numNewlines < 4) {
            builder.append('\n');
            numNewlines++;
        }
        return builder.toString();
    }

    @Nullable
    private static PedKeyPress waitForKeyPress(
            BlockingQueue<Integer> keyPresses,
            long timeout,
            TimeUnit timeUnit
    ) {
        keyPresses.clear();
        Integer poll;
        try {
            poll = keyPresses.poll(timeout, timeUnit);
            if (poll == null) {
                return null;
            }
        } catch (InterruptedException ignore) {
            LOGGER.info("Interrupted whilst waiting for key press?");
            return null;
        }

        return PedKeyPress.valueOf(poll);
    }

    private static void userPrintln(
            MpiClient mpiClient,
            String text,
            Object... vargs
    ) throws IOException {
        // todo warn if string is too long. 21 chars max
        // todo make version that has all 3 or 4 text lines as input? userPrintScreen() ?
        String msg = String.format(text, vargs);

        LOGGER.debug("userPrintln:\n'{}'", StringEscapeUtils.escapeJava(msg));
        boolean ok = mpiClient.displayText(MPI, msg, true, true, true);
        if (!ok) {
            throw new IOException("displayText failed?!");
        }
    }

    enum ContinueType {
        AnyKey, AcceptOnly, AcceptAndCancel
    }

    public enum UserDigitInputType {
        Selection, Amount;

        public int getPromptsIndex() {
            switch (this) {
                case Selection:
                    return 1; // "    Please Select"
                case Amount:
                    return 4; // "Amount?"
                default:
                    return 1;
            }
        }
    }

    private static final class MenuChoice {
        final String mName;
        final int mPrice;

        MenuChoice(String name, int price) {
            mName = name;
            mPrice = price;
        }

        @Override
        public String toString() {
            double d = (double) mPrice / 100.0d;
            return String.format("%s (£%.2f)", mName, d);
        }
    }
}

/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.sampleapps.commandline;

import static com.miurasystems.examples.transactions.EmvTransactionType.Chip;
import static com.miurasystems.examples.transactions.EmvTransactionType.Contactless;

import android.support.annotation.Nullable;

import com.miurasystems.examples.rki.MiuraRKIManager;
import com.miurasystems.examples.transactions.EmvChipInsertStatus;
import com.miurasystems.examples.transactions.EmvTransaction;
import com.miurasystems.examples.transactions.EmvTransactionException;
import com.miurasystems.examples.transactions.EmvTransactionSummary;
import com.miurasystems.examples.transactions.EmvTransactionType;
import com.miurasystems.examples.transactions.MagSwipeError;
import com.miurasystems.examples.transactions.MagSwipePinResult;
import com.miurasystems.examples.transactions.MagSwipeSignatureResult;
import com.miurasystems.examples.transactions.MagSwipeSummary;
import com.miurasystems.examples.transactions.MagSwipeTransaction;
import com.miurasystems.examples.transactions.MagSwipeTransactionException;
import com.miurasystems.examples.transactions.PaymentMagType;
import com.miurasystems.examples.transactions.SignatureSummary;
import com.miurasystems.examples.transactions.UserInputType;
import com.miurasystems.miuralibrary.MpiClient;
import com.miurasystems.miuralibrary.Result;
import com.miurasystems.miuralibrary.api.objects.P2PEStatus;
import com.miurasystems.miuralibrary.comms.Connector;
import com.miurasystems.miuralibrary.enums.InterfaceType;
import com.miurasystems.miuralibrary.events.MpiEvents;
import com.miurasystems.miuralibrary.tlv.CardData;

import org.apache.commons.lang3.StringUtils;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.NonBlockingReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;



final class InteractiveTransactionDemo {

    private static final Logger LOGGER = LoggerFactory.getLogger(InteractiveTransactionDemo.class);
    private static final NonBlockingReader TERMINAL_READER;
    private static final Terminal TERMINAL;
    private static final Scanner SCANNER;
    private static final Attributes CANONICAL_ATTRIBUTES;
    private static final char BACKSPACE_CHAR = '\u0008';
    private static final int UNKNOWN_INPUT = -1;
    private static final int CANCEL_KEY = -2;
    private static final int GBP = 826;

    static {
        try {
            TERMINAL = TerminalBuilder.builder()
                    .jna(true)
                    .system(true)
                    .build();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        SCANNER = new Scanner(System.in);

        // raw mode means we get key presses rather than line buffered input.
        // To return the terminal to 'normal mode' we can use the old Attributes,
        // which are returned from enterRawMode
        CANONICAL_ATTRIBUTES = TERMINAL.enterRawMode();

        // Shutdown hook to ensure that the terminal is closed and reset.
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                TERMINAL.setAttributes(CANONICAL_ATTRIBUTES);
                try {
                    TERMINAL.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                SCANNER.close();
            }
        });

        TERMINAL_READER = TERMINAL.reader();
    }

    private InteractiveTransactionDemo() {
    }

    static void runInteractive(CommandLineArguments cmdLine) {

        Connector connector = cmdLine.getConnector();
        if (connector == null) {
            throw new AssertionError("Connector expected?");
        }

        MpiEvents events = new MpiEvents();
        MpiClient client = new MpiClient(connector, events);

        userPrintln("Connecting to %s", cmdLine.getAddress());
        try {
            client.openSession();
        } catch (IOException e) {
            userPrintln("Failed to connect to device. Cancelling transaction");
            client.closeSession();
            return;
        }

        try {
            ensurePedHasKeys(client);
        } catch (IOException e) {
            userPrintln("Cancelling Transaction");
            client.closeSession();
            return;
        }

        int total = -1;
        try {
            total = getTotalFromPOS();
        } catch (IOException e) {
            LOGGER.debug("IOException from getTotalFromPOS", e);
        }
        if (total < 0) {
            userPrintln("Cancelling Transaction!");
            client.closeSession();
            return;
        }

        boolean success;
        try {
            success = performTransaction(client, events, total, Thread.currentThread());
        } finally {
            userPrintln("Closing connection to %s", cmdLine.getAddress());
            client.closeSession();
        }
        if (success) {
            userPrintln("Transaction Success!");
        } else {
            userPrintln("Transaction Failed!");
        }
        client.closeSession();
    }

    private static void ensurePedHasKeys(MpiClient client) throws IOException {
        P2PEStatus p2PEStatus = client.p2peStatus(InterfaceType.MPI);
        if (p2PEStatus == null) {
            throw new IOException("Couldn't get P2PEStatus");
        }
        boolean hasKey = p2PEStatus.isSREDReady || p2PEStatus.isPINReady;
        if (hasKey) {
            return;
        }

        char keyPress = readUserKeyPress(
                "PED has no keys! Inject keys? (Y/n) ",
                true, true
        );
        switch (keyPress) {
            case '\n':
            case 'Y':
            case 'y':
                userPrintln("Injecting keys...");
                MiuraRKIManager.injectKeys(client);
                userPrintln("...keys injected OK!");
                return;
            default:
                String message = "Can't perform transaction without keys.";
                userPrintln(message);
                throw new IOException(message);
        }
    }

    private static boolean performTransaction(
            MpiClient mpiClient,
            MpiEvents events,
            int total,
            Thread mainThread
    ) {
        // todo need to monitor device status
        // Todo perform standard chapter 4.3 checks

        // Enable card events
        BlockingQueue<CardData> cardStatus = new LinkedBlockingQueue<>();
        events.CardStatusChanged.register(cardData -> {
            handleCardData(mainThread, cardStatus, cardData);
        });

        mpiClient.cardStatus(InterfaceType.MPI, true, false, true, true, true);

        TryContactlessResult contactlessResult = tryContactlessTransaction(mpiClient, total);
        switch (contactlessResult) {
            case ContactlessSuccess:
                return true;
            case ContactlessError:
                return false;
            case CardSwiped:
                userPrintln("Card swiped.");
                break;
            case CardInserted:
                userPrintln("Card inserted.");
                break;
            case TrySwipeOrInsert:
                userPrintln("Contactless cancelled. Try inserting or swiping card.");
                break;
        }

        CardData cardData = waitForCardDataEvent(cardStatus);
        if (cardData == null) {
            return false;
        }
        mpiClient.cardStatus(InterfaceType.MPI, false, false, false, false, false);

        EmvChipInsertStatus insertStatus = EmvTransaction.canProcessEmvChip(cardData);
        if (insertStatus != EmvChipInsertStatus.NoCardPresentError) {
            return performChipTransaction(mpiClient, total, insertStatus);
        }

        Result<MagSwipeSummary, MagSwipeError> swipeResult =
                MagSwipeTransaction.canProcessMagSwipe(cardData);
        if (swipeResult.isSuccess()) {
            return performSwipeTransaction(mpiClient, total, swipeResult.asSuccess().getValue());
        } else {
            userPrintln("Received cardData that is not magswipe or card insert?:\n%s", cardData);
            return false;
        }
    }

    private static void handleCardData(Thread mainThread,
            BlockingQueue<CardData> cardStatus,
            CardData cardData
    ) {
        // todo only accept cardPresent and swipes?

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
                userPrintln("cardStatus.offer failed");
                mainThread.interrupt();
            }
        }
    }

    private static TryContactlessResult tryContactlessTransaction(MpiClient mpiClient, int total) {
        /*
            Prime contactless transaction.
            It will be aborted by the PED if a card is swiped/inserted
        */
        EmvTransaction ctlsTransaction = new EmvTransaction(mpiClient, Contactless);

        try {
            EmvTransactionSummary ctlsResult = ctlsTransaction.startTransaction(total, GBP);
            mpiClient.cardStatus(InterfaceType.MPI, false, false, false, false, false);
            printEmvSuccess(ctlsResult);
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
                    userPrintln("Contactless cannot be used. Insert or swipe card");
                    return TryContactlessResult.TrySwipeOrInsert;
                case CONTACTLESS_INSERT_CARD:
                case CONTACTLESS_INSERT_SWIPE_OR_OTHER_CARD:
                    userPrintln(
                            "Contactless cannot be used with this card. Insert or swipe it");
                    return TryContactlessResult.TrySwipeOrInsert;
                case CONTACTLESS_TIMEOUT:
                    userPrintln("Contactless timed out!");
                    return TryContactlessResult.ContactlessError;
                case USER_CANCELLED:
                    userPrintln("User cancelled transaction on PED!");
                    return TryContactlessResult.ContactlessError;
                default:
                    printEmvError(ctlsError, Contactless);
                    return TryContactlessResult.ContactlessError;
            }
        }
    }

    private static boolean performSwipeTransaction(
            MpiClient mpiClient,
            int total,
            MagSwipeSummary magSwipeSummary
    ) {
        MagSwipeTransaction swipeTransaction = new MagSwipeTransaction(mpiClient);

        UserInputType userInputType = UserInputType.resolvePaymentType(
                PaymentMagType.Auto, magSwipeSummary
        );

        userPrintln("Processing swipe transaction.");
        try {
            if (userInputType == UserInputType.Pin) {
                userPrintln("Enter PIN on PED.");

                MagSwipePinResult pinResult = swipeTransaction.processPinTransaction(
                        magSwipeSummary, total, GBP);

                userPrintln("PIN ok!");
                userPrintln("Online PIN success\nPIN block: %s\nKSN: %s",
                        pinResult.mOnlinePinSummary.mPinData,
                        pinResult.mOnlinePinSummary.mPinKSN
                );

            } else {

                String signatureInput = readUserInputText("Enter signature: ");
                LOGGER.debug("Signature input: {}", signatureInput);

                SignatureSummary signature = new SignatureSummary(signatureInput);
                MagSwipeSignatureResult signatureResult =
                        swipeTransaction.processSignatureTransaction(
                                magSwipeSummary, total, GBP, signature
                        );
            }

            userPrintln("MagSwipeSummary:");
            userPrintln("\tMaskedTrack2Data: %s", magSwipeSummary.mMaskedTrack2Data);
            userPrintln("\tSredKSN: %s", magSwipeSummary.mSredKSN);
            userPrintln("\tSredKSN: %s", magSwipeSummary.mSredKSN);
            userPrintln("\tIsPinRequired: %s", magSwipeSummary.mIsPinRequired);
            if (magSwipeSummary.mPlainTrack1Data != null) {
                userPrintln("\tmPlainTrack1Data: %s", magSwipeSummary.mPlainTrack1Data);
            }
            if (magSwipeSummary.mPlainTrack2Data != null) {
                userPrintln("\tmPlainTrack2Data: %s", magSwipeSummary.mPlainTrack2Data);
            }

            userPrintln("Transaction complete!");
            return true;

        } catch (MagSwipeTransactionException swipeException) {
            userPrintln("Error performing swipe transaction");
            userPrintln(swipeException.mErrorCode.toString());
            String message = swipeException.getMessage();
            if (!StringUtils.isBlank(message)) {
                userPrintln(message);
            }
            return false;
        }
    }

    private static boolean performChipTransaction(
            MpiClient mpiClient,
            int total,
            EmvChipInsertStatus insertStatus
    ) {
        if (insertStatus == EmvChipInsertStatus.CardIncompatibleError) {
            userPrintln("Inserted card is incompatible. Cancelling transaction");
            return false;
        } else if (insertStatus != EmvChipInsertStatus.CardInsertedOk) {
            throw new AssertionError();
        }

        EmvTransaction emvTransaction = new EmvTransaction(mpiClient, Chip);

        userPrintln("Processing transaction");

        try {
            EmvTransactionSummary result = emvTransaction.startTransaction(total, GBP);
            printEmvSuccess(result);
            return true;
        } catch (EmvTransactionException exception) {
            printEmvError(exception, Chip);
            return false;
        }
    }

    private static void printEmvError(
            EmvTransactionException emvError,
            EmvTransactionType transactionType
    ) {
        userPrintln("Error performing " + transactionType + " transaction:");
        userPrintln('\t' + emvError.mErrCode.toString());
        String message = emvError.getMessage();
        if (!StringUtils.isBlank(message)) {
            userPrintln("\t'message: %s'", message);
        }
    }

    private static void printEmvSuccess(EmvTransactionSummary summary) {
        userPrintln("Transaction success!");
        userPrintln(summary.mStartTransactionResponse);
        userPrintln(summary.mContinueTransactionResponse);
    }

    @Nullable
    private static CardData waitForCardDataEvent(BlockingQueue<CardData> cardStatus) {
        // wait for card event
        long timeout = 15L;
        CardData cardData;
        try {
            cardData = cardStatus.poll(timeout, TimeUnit.SECONDS);
            if (cardData == null) {
                userPrintln(
                        "Card not inserted after %d seconds! Cancelling transaction",
                        timeout
                );
                return null;
            }
        } catch (InterruptedException ignore) {
            userPrintln("Interrupted whilst reading card data? Cancelling transaction");
            return null;
        }
        return cardData;
    }

    /** POS as in till operator */
    private static int getTotalFromPOS() throws IOException {
        MenuChoice menuChoice = showInteractiveMenu();
        if (menuChoice == null) {
            return -1;
        }

        userPrintln("How many %s would you like? (0-9)", menuChoice.mName);
        int quantity = readUserDigit(0, 9, true, true, true);
        if (quantity < 0) {
            return -1;
        }

        int tip = readUserNumberInput("Would you like to enter a Tip?", 10, true);
        if (tip < 0) {
            return -1;
        }

        int total = (menuChoice.mPrice * quantity) + tip;

        userPrintln(String.format(
                "Total due: £%.2f\n"
                        + "Pay by swipe, contactless, or insert card",
                (double) total / 100.0d
        ));

        return total;
    }

    @Nullable
    private static MenuChoice showInteractiveMenu() throws IOException {

        List<MenuChoice> menuChoices = Arrays.asList(
                new MenuChoice("Chips", 200),
                new MenuChoice("Chips & Gravy", 250),
                new MenuChoice("Fish, Chips, & Gravy", 550)
        );

        StringBuilder builder = new StringBuilder(512);
        builder.append("Select from menu:\n");
        for (int i = 0; i < menuChoices.size(); i++) {
            MenuChoice choice = menuChoices.get(i);
            builder.append(String.format("  %d. %s\n", i + 1, choice));
        }
        builder.append('\n');
        builder.append("Press 'c' to cancel transaction");
        userPrintln(builder.toString());

        int digit = readUserDigit(1, menuChoices.size(), true, true, true);
        if (digit < 1) {
            return null;
        }
        return menuChoices.get(digit - 1);
    }

    private static String readUserInputText(String prompt) {
        Attributes current = TERMINAL.getAttributes();
        TERMINAL.setAttributes(CANONICAL_ATTRIBUTES);

        userPrint(prompt);
        String signatureInput = SCANNER.nextLine().trim();
        TERMINAL.setAttributes(current);
        return signatureInput;
    }

    private static int readUserNumberInput(
            String prompt,
            int maxDigitCount,
            boolean currency
    ) throws IOException {
        StringBuilder builder = new StringBuilder(maxDigitCount);

        userPrintln(prompt);

        while (true) {
            String inputPrompt = getInputPrompt(builder, maxDigitCount, currency);
            char keyPress = readUserKeyPress(inputPrompt, false, false);

            switch (keyPress) {
                case 'c':
                    userPrintln("\nInput cancelled");
                    return CANCEL_KEY;
                case '\r':
                case '\n':
                    String s = formatNumberString(builder, false);
                    try {
                        //noinspection TooBroadScope
                        int i = Integer.parseInt(s, 10);
                        userPrint("\n"); // only print if we are going to return value
                        return i;
                    } catch (NumberFormatException ignore) {
                        userPrintln(String.format(
                                "\rNumber too large: %s. Must be < %d", s, Integer.MAX_VALUE
                                )
                        );
                    }
                    break;
                case BACKSPACE_CHAR:
                    int length = builder.length();
                    if (length > 0) {
                        // Erase the last char from buffer
                        builder.deleteCharAt(length - 1);
                        // Erase it from screen
                        userPrint(BACKSPACE_CHAR + " ");
                    }
                    break;
                default:
                    if (Character.isDigit(keyPress)) {
                        if (builder.length() < maxDigitCount) {
                            builder.append(keyPress);
                        } else {
                            userPrintln("\rInput too big! Only %d digits allowed", maxDigitCount);
                        }
                    } else {
                        userPrintln(String.format(
                                "\rBad input '%c' (U+%d)", keyPress, (int) keyPress)
                        );
                    }
                    break;
            }

        }
    }

    private static String getInputPrompt(StringBuilder builder,
            int maxNumberSize,
            boolean showDecimal
    ) {
        String prefix = showDecimal ? "£" : "";
        String number = formatNumberString(builder, showDecimal);
        int padSize = showDecimal ? (maxNumberSize + 1) : maxNumberSize;
        String paddedNumber = StringUtils.leftPad(number, padSize, ' ');
        // \r to "keep" the current input line
        return String.format("\r>%s %s", prefix, paddedNumber);
    }

    private static String formatNumberString(StringBuilder builder, boolean showDecimal) {

        if (!showDecimal) {
            if (builder.length() == 0) {
                return "0";
            }
            return builder.toString();
        }

        int length = builder.length();
        if (length >= 3) {
            String left = builder.substring(0, length - 2);
            String right = builder.substring(length - 2);
            return String.format("%s.%s", left, right);
        }

        return "0." + StringUtils.leftPad(builder.toString(), 2, '0');
    }

    private static int readUserDigit(int minChoice,
            int maxChoice,
            boolean optionPrompt,
            boolean echo,
            boolean echoNewline
    ) throws IOException {

        String format;
        if (optionPrompt) {
            format = String.format("Select option (%d-%d): ", minChoice, maxChoice);
        } else {
            format = "";
        }
        char userKeyPress = readUserKeyPress(format, echo, echoNewline);

        //noinspection ImplicitNumericConversion
        if (userKeyPress == 'c') {
            userPrintln("Transaction cancelled");
            return CANCEL_KEY;
        } else if (!Character.isDigit(userKeyPress)) {
            userPrintln("Bad input '%c' (U+%d). Transaction cancelled",
                    userKeyPress,
                    (int) userKeyPress
            );
            return UNKNOWN_INPUT;
        }

        int digit = Character.digit(userKeyPress, 10);
        if ((digit < minChoice) || (digit > maxChoice)) {
            userPrintln("Choice %d not in menu!", digit);
            return UNKNOWN_INPUT;
        }
        return digit;
    }

    private static char readUserKeyPress(String prompt, boolean echo, boolean echoNewline)
            throws IOException {
        if (!StringUtils.isEmpty(prompt)) {
            userPrint(prompt);
        }

        int read = TERMINAL_READER.read();

        if (read < 0) {
            throw new AssertionError("Unable to read stdin? Got " + read);
        } else if (!Character.isValidCodePoint(read)) {
            String msg = "Invalid codepoint? U+" + read;
            throw new AssertionError(msg);
        } else if (!Character.isBmpCodePoint(read)) {
            String msg = "Can't read Unicode outside Basic Multilingual Plane: U+" + read;
            throw new AssertionError(msg);
        }

        //noinspection NumericCastThatLosesPrecision
        char c = (char) read;

        if (echo) {
            // Echo the value back and finish the line
            String s = String.valueOf(c);
            if (echoNewline) {
                userPrintln(s);
            } else {
                userPrint(s);
            }
        }
        return c;
    }

    private static void userPrintln(String text, Object... vargs) {
        System.out.println(String.format(text, vargs));
    }

    private static void userPrint(String text, Object... vargs) {
        System.out.print(String.format(text, vargs));
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

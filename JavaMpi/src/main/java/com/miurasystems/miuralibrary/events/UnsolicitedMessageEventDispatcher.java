/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.events;

import android.support.annotation.NonNull;

import com.miurasystems.miuralibrary.CommandUtil;
import com.miurasystems.miuralibrary.comms.ResponseMessage;
import com.miurasystems.miuralibrary.enums.DeviceStatus;
import com.miurasystems.miuralibrary.enums.M012Printer;
import com.miurasystems.miuralibrary.tlv.BinaryUtil;
import com.miurasystems.miuralibrary.tlv.CardData;
import com.miurasystems.miuralibrary.tlv.Description;
import com.miurasystems.miuralibrary.tlv.HexUtil;
import com.miurasystems.miuralibrary.tlv.TLVObject;
import com.miurasystems.miuralibrary.tlv.TLVParser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public final class UnsolicitedMessageEventDispatcher {

    /** SLF4J Logger */
    private static final Logger LOGGER =
            LoggerFactory.getLogger(UnsolicitedMessageEventDispatcher.class);

    public static void signalEvent(
            @NonNull ResponseMessage responseMessage,
            @NonNull MpiEvents events
    ) {

        List<TLVObject> tlvObjects = TLVParser.decode(responseMessage.getBody());

        //check Device Status
        TLVObject tlvObjectDeviceStatus = CommandUtil.firstMatch(tlvObjects, Description.Status_Code);
        TLVObject tlvObjectDeviceStatusMsg = CommandUtil.firstMatch(tlvObjects, Description.Status_Text);
        if (tlvObjectDeviceStatus != null && tlvObjectDeviceStatusMsg != null) {
            LOGGER.debug("DeviceStatusChanged");
            checkDeviceStatus(events, tlvObjectDeviceStatus, tlvObjectDeviceStatusMsg);
            return;
        }

        //check Key Pressed
        TLVObject tlvKeyPressed = CommandUtil.firstMatch(tlvObjects, Description.Keyboard_Data);
        if (tlvKeyPressed != null) {
            LOGGER.debug("KeyPressed");
            checkKey(events, tlvKeyPressed);
            return;
        }

        TLVObject tlvObjectCardStatus = CommandUtil.firstMatch(tlvObjects, Description.Card_Status);
        if (tlvObjectCardStatus != null) {
            LOGGER.debug("CardStatusChanged");
            //check only if contains that tag, parse level up tag
            TLVObject tlvCardStatusObject = CommandUtil.firstMatch(tlvObjects, Description.Response_Data);
            if (tlvCardStatusObject != null) {
                checkCardStatus(events, tlvCardStatusObject);
            }
            return;
        }

        //check if something was scanned
        TLVObject tlvObjectBarcode = CommandUtil.firstMatch(tlvObjects, Description.Scanned_Data);
        if (tlvObjectBarcode != null) {
            LOGGER.debug("BarcodeScanned");
            events.BarcodeScanned.notifyListener(tlvObjectBarcode.getData());
            return;
        }

        TLVObject tlvPrinterStatus = CommandUtil.firstMatch(tlvObjects, Description.Printer_Status );
        if (tlvPrinterStatus != null ) {
            LOGGER.debug("PrinterStatusChanged");
            checkM012PrinterStatus(events, tlvPrinterStatus);
            return;
        }

        //Check serial port data
        TLVObject tlvUSBSerialData = CommandUtil.firstMatch(tlvObjects, Description.USB_SERIAL_DATA);
        if (tlvUSBSerialData != null) {
            LOGGER.debug("UsbSerialPortDataReceived");
            events.UsbSerialPortDataReceived.notifyListener(tlvUSBSerialData.getRawData());
            return;
        }

        LOGGER.info("Unhandled unsolicited message!");
    }

    private static void checkM012PrinterStatus(MpiEvents events, TLVObject tlvObject) {
        byte value = tlvObject.getRawData()[0];
        M012Printer m012Printer = M012Printer.getByValue(value);
        if (m012Printer != null) {
            events.PrinterStatusChanged.notifyListener(m012Printer);
        }
    }

    private static void checkDeviceStatus(
            MpiEvents events,
            TLVObject tlvObjectStatus,
            TLVObject tlvObjectMsg
    ) {
        byte value = tlvObjectStatus.getRawData()[0];
        DeviceStatus deviceStatus = DeviceStatus.getByValue(value);
        if (deviceStatus != null) {
            DeviceStatusChange deviceStatusChange = new DeviceStatusChange(
                    deviceStatus, HexUtil.bytesToString(tlvObjectMsg.getRawData()));
            events.DeviceStatusChanged.notifyListener(deviceStatusChange);
        }
    }

    private static void checkKey(MpiEvents events, TLVObject tlvObject) {
        int value = BinaryUtil.ubyteToInt(tlvObject.getRawData()[0]);
        events.KeyPressed.notifyListener(value);
    }

    private static void checkCardStatus(MpiEvents events, @NonNull TLVObject tlvObject) {
        CardData cardData = CardData.valueOf(tlvObject);
        events.CardStatusChanged.notifyListener(cardData);
    }

}

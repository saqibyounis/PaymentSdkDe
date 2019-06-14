/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary;

import static com.miurasystems.miuralibrary.tlv.BinaryUtil.ubyteToInt;

import android.support.annotation.Nullable;

public enum CommandType {
    Reset_Device(0xD0, 0x00),
    Get_Configuration(0xD0, 0x01),
    Get_DeviceInfo(0xD0, 0x02),
    Select_File(0x00, 0xA4),
    Read_Binary(0x00, 0xB0),
    Update_Binary(0x00, 0xD6),
    Stream_Binary(0x00, 0xD7),
    Card_Status(0xD0, 0x60),
    Keyboard_Status(0xD0, 0x61),
    Battery_Status(0xD0, 0x62),
    Bluetooth_Control(0xD0, 0xBC),
    Display_Text(0xD2, 0x01),
    Display_Image(0xD2, 0xD2),
    Configure_Image(0xD2, 0xB0),
    Spool_print(0xD2, 0xA3),
    Print_Text(0xD2, 0xA1),
    Print_Image(0xD2, 0xA2),
    Get_Numeric_Data(0xD2, 0x04),
    Get_Dynamic_Tip(0xD2, 0x05),
    Get_Secure_PAN(0xD2, 0x5A),
    Get_Next_Transaction_Sequence_Counter(0xDE, 0x02),
    Get_EMV_Hash_Values(0xDE, 0x01),
    Get_Contactless_Hash_Values(0xDE, 0xC2),
    Start_Transaction(0xDE, 0xD1),
    Continue_Transaction(0xDE, 0xD2),
    Start_Contactless_Transaction(0xDE, 0xC1),
    Abort(0xD0, 0xFF),
    Online_PIN(0xDE, 0xD6),
    System_Clock(0xD0, 0x10),
    USB_Serial_Disconnect(0xD0, 0xC0),

    P2PE_Status(0xEE, 0xE0),
    P2PE_Initialise(0xEE, 0xE1),
    P2PE_Import(0xEE, 0xE2),
    P2PE_Get_Encrypted_Data(0xEE, 0xE3),
    P2PE_Get_KSN_For_MAC(0xEE, 0xE5),
    P2PE_Verify_MAC(0xEE, 0xE4),
    P2PE_Get_MAC_Configuration_File(0xEE, 0xE6),

    System_Log(0xD0, 0xE1),

    // RPI specific commands
    Spool_Text(0xD2, 0xA3),
    print_ESCPOS(0xD2, 0xA4),
    Cash_Drawer(0xD0, 0xD0),
    Bar_Code_Scanner_Status(0xD0, 0xD1),
    Peripheral_Status(0xD0, 0xA0),
    Printer_Status(0xD0, 0x63),
    Setup_USB_Serial_Adaptor(0xD0, 0x5C),
    Send_USB_Serial_Data(0xD0, 0x5D);

    public final int Cla;
    public final int Ins;

    CommandType(int cla, int ins) {
        this.Cla = cla;
        this.Ins = ins;
    }

    @Nullable
    public static CommandType valueOf(byte cla, byte ins) {
        return CommandType.valueOf(ubyteToInt(cla), ubyteToInt(ins));
    }

    @Nullable
    public static CommandType valueOf(int cla, int ins) {
        for (CommandType type : CommandType.values()) {
            if (type.Cla == cla && type.Ins == ins) {
                return type;
            }
        }
        return null;
    }

}

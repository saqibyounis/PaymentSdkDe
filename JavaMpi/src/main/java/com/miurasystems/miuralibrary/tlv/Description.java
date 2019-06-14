/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.tlv;


public enum Description {
    UNKNOWN(0xFFFFFFFF),
    Pin_Digit_Status(0xdfa101),
    Pin_Entry_Status(0xdfa102),
    Message_Authentication_Code(0xdfae06),
    MAC_Result(0xdfae07),
    ICC_Answer_To_Reset(0x63),
    Date(0x9a),
    Time(0x9f21),
    File_Size(0x80),
    Battery_Status(0xdfa20a),
    Charging_Status(0xdfa209),
    Transaction_Sequence_Counter(0x9f41),
    Issuer_Script_Template_1_71(0x71),
    Issuer_Script_Template_1_72(0x72),
    Issuer_Authentication_Data(0x91),
    Card_Status(0x48),
    AID(0x4f),
    Application_Label(0x50),
    Track_2_Equivalent_Data(0x57),
    Application_Primary_Account_Number_PAN(0x5a),
    Cardholder_Name(0x5f20),
    Language_Preference(0x5f2d),
    Application_Expiration_Date(0x5f24),
    Application_Effective_Date(0x5f25),
    Issuer_Country_Code(0x5f28),
    Transaction_Currency_Code(0x5f2a),
    Service_Code(0x5f30),
    Application_Primary_Account_Number_PAN_Sequence_Number(0x5f34),
    Transaction_Currency_Exponent(0x5f36),
    Application_Template(0x61),
    FCI_Template(0x6f),
    Read_Record_response(0x70),
    Response_Message_Template_Format_2(0x77),
    Response_Message_Template_Format_1(0x80),
    Amount_Authorised_Binary(0x81),
    Application_Interchange_Profile(0x82),
    DF_Name(0x84),
    Issuer_Script_Command(0x86),
    Application_Priority_Indicator(0x87),
    SFI(0x88),
    Authorisation_Response_Code(0x8a),
    Card_Risk_Management_Data_Object_List_1_CDOL1(0x8c),
    Card_Risk_Management_Data_Object_List_2_CDOL2(0x8d),
    Cardholder_Verification_Method_CVM_List(0x8e),
    Certification_Authority_Public_Key_Index(0x8f),
    Issuer_Public_Key_Certificate(0x90),
    Issuer_Public_Key_Remainder(0x92),
    Signed_Static_Application_Data(0x93),
    Application_File_Locator_AFL(0x94),
    Terminal_Verification_Results(0x95),
    Transaction_Certificate_Data_Object_List_TDOL(0x97),
    Transaction_Status_Information(0x9b),
    Transaction_Type(0x9c),
    Transaction_Information_Status_sale(0x9c00),
    Transaction_Information_Status_cash(0x9c01),
    Transaction_Information_Status_cashback(0x9c09),
    Acquirer_Identifier(0x9f01),
    Amount_Authorised_Numeric(0x9f02),
    Amount_Other_Numeric(0x9f03),
    Amount_Other_Binary(0x9f04),
    Application_Discretionary_Data(0x9f05),
    Application_Identifier_AID_terminal(0x9f06),
    Application_Usage_Control(0x9f07),
    ICC_Application_Version_Number(0x9f08),
    Term_Application_Version_Number(0x9f09),
    Cardholder_Name_Extended(0x9f0b),
    Issuer_Action_Code_Default(0x9f0d),
    Issuer_Action_Code_Denial(0x9f0e),
    Issuer_Action_Code_Online(0x9f0f),
    Issuer_Application_Data(0x9f10),
    Issuer_Code_Table_Index(0x9f11),
    Application_Preferred_Name(0x9f12),
    Last_Online_Application_Transaction_Counter_ATC_Register(0x9f13),
    Lower_Consecutive_Offline_Limit(0x9f14),
    Personal_Identification_Number_PIN_Try_Counter(0x9f17),
    Issuer_Script_Identifier(0x9f18),
    Terminal_Country_Code(0x9f1a),
    Terminal_Floor_Limit(0x9f1b),
    Terminal_ID(0x9f1c),
    Interface_Device_Serial_Number(0x9f1e),
    Track_1_Discretionary_Data(0x9f1f),
    Track_2_Discretionary_Data(0x9f20),
    Upper_Consecutive_Offline_Limit(0x9f23),
    Application_Cryptogram(0x9f26),
    Cryptogram_Information_Data(0x9f27),
    ICC_PIN_Encipherment_Public_Key_Certificate(0x9f2d),
    ICC_PIN_Encipherment_Public_Key_Exponent(0x9f2e),
    ICC_PIN_Encipherment_Public_Key_Remainder(0x9f2f),
    Issuer_Public_Key_Exponent(0x9f32),
    Terminal_Capabilities(0x9f33),
    Cardholder_Verification_Method_CVM_Results(0x9f34),
    Terminal_Type(0x9f35),
    Application_Transaction_Counter_ATC(0x9f36),
    Unpredictable_Number(0x9f37),
    Processing_Options_Data_Object_List_PDOL(0x9f38),
    Application_Reference_Currency(0x9f3b),
    Terminal_Capabilities_Add(0x9f40),
    Application_Currency_Code(0x9f42),
    Application_Reference_Currency_Exponent(0x9f43),
    Application_Currency_Exponent(0x9f44),
    ICC_Public_Key_Certificate(0x9f46),
    ICC_Public_Key_Exponent(0x9f47),
    ICC_Public_Key_Remainder(0x9f48),
    Dynamic_Data_Authentication_Data_Object_List_DDOL(0x9f49),
    Static_Data_Authentication_Tag_List(0x9f4a),
    Signed_Dynamic_Application_Data(0x9f4b),
    ICC_Dynamic_Number(0x9f4c),
    Issuer_Script_Results(0x9f5b),
    FCI_Proprietary_Template(0xa5),
    File_Control_Information_FCI_Issuer_Discretionary_Data(0xbf0c),
    Decision(0xc0),
    Acquirer_Index(0xc2),
    Status_Code(0xc3),
    Status_Text(0xc4),
    PIN_Retry_Counter(0xc5),
    Identifier(0xdf0d),
    Cardholder_Verification_Status(0xdf28),
    Version(0xdf7f),
    Command_Data(0xe0),
    Response_Data(0xe1),
    Decision_Required(0xe2),
    Transaction_Approved(0xe3),
    Online_Authorisation_Required(0xe4),
    Transaction_Declined(0xe5),
    Terminal_Status_Changed(0xe6),
    Configuration_Information(0xed),
    Software_Information(0xef),
    Terminal_Action_Code_DEFAULT(0xff0d),
    Terminal_Action_Code_OFFLINE(0xff0e),
    Terminal_Action_Code_ONLINE(0xff0f),
    PIN_Digit_Status(0xdfa201),
    PIN_Entry_Status(0xdfa202),
    Configure_TRM_Stage(0xdfa203),
    Configure_Application_Selection(0xdfa204),
    Keyboard_Data(0xdfa205),
    Secure_Prompt(0xdfa206),
    Number_Format(0xdfa207),
    Numeric_Data(0xdfa208),
    Amount_Line(0xdfa20e),
    Dynamic_Tip_Percentages(0xdfa217),
    Dynamic_Tip_Template(0xdfa218),
    Stream_Offset(0xdfa301),
    Stream_Size(0xdfa302),
    Stream_timeout(0xdfa303),
    File_md5sum(0xdfa304),
    P2PE_Status(0xdfae01),
    SRED_Data(0xdfae02),
    SRED_KSN(0xdfae03),
    Online_PIN_Data(0xdfae04),
    Online_PIN_KSN(0xdfae05),
    Track_1(0x5F21),
    Track_2(0x5F22),
    Track_3(0x5F23),
    JIS2_Track_1(0xDFA121),
    JIS2_Track_2(0xDFA122),
    JIS2_Track_3(0xDFA123),
    Masked_Track_2(0xdfae22),
    ICC_Masked_Track_2(0xdfae57),
    Masked_PAN(0xdfae5A),
    Transaction_Info_Status_bits(0xdfdf00),
    Revoked_certificates_list(0xdfdf01),
    Online_DOL(0xdfdf02),
    Referral_DOL(0xdfdf03),
    ARPC_DOL(0xdfdf04),
    Reversal_DOL(0xdfdf05),
    AuthResponse_DO(0xdfdf06),
    PSE_Directory(0xdfdf09),
    Threshold_Value_for_Biased_Random_Selection(0xdfdf10),
    Target_Percentage_for_Biased_Random_Selection(0xdfdf11),
    Maximum_Target_Percentage_for_Biased_Random_Selection(0xdfdf12),
    Default_CVM(0xdfdf13),
    Issuer_script_size_limit(0xdfdf16),
    Log_DOL(0xdfdf17),
    Partial_AID_Selection_Allowed(0xe001),
    Transaction_Category_Code(0x9f53),
    Balance_Before_Generate_AC(0xdf8104),
    Balance_After_Generate_AC(0xdf8105),
    Scanned_Data(0xdfab01),
    Encrypted_Data(0xEE),
    Printer_Status(0xdfa401),
    Contactless_Kernel_And_Mode(0xdf30),
    Form_Factor_Indicator(0x9f6e),
    Terminal_Transcation_Qualifier(0x9f66),
    POS_Entry_Mode(0x9f39),
    Mobile_Support_Indicator(0x9f7e),
    Payment_Cancel_Or_PIN_Entry_Timeout(0x08),
    Payment_Internal_1(0x09),
    Payment_Internal_2(0x0A),
    Payment_Internal_3(0x0B),
    Payment_User_Bypassed_PIN(0x0C),
    USB_SERIAL_DATA(0xDFAB02),
    TERMINAL_LANUAGE_PREFERENCE(0xDFA20C);


    /**
     * tag number
     */
    private final int tag;

    public int undefinedTag;

    private Description(int tag) {
        this.tag = tag;
    }

    public static Description valueOf(int tag) {

        Description[] descriptions = values();
        for (Description description : descriptions) {
            if (description.getTag() == tag) {
                return description;
            }
        }

        return UNKNOWN;
    }

    public int getTag() {

        if (this == UNKNOWN) {
            return this.undefinedTag;
        } else {
            return this.tag;
        }
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.name());
        sb.append("(");
        sb.append(String.format("0x%4s", Integer.toHexString(this.getTag())).replace(" ", "0"));
        sb.append(")");
        return sb.toString();
    }
}

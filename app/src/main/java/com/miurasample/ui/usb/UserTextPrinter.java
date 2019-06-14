/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasample.ui.usb;

public interface UserTextPrinter {
    void log(String tag, String message);
    void log(String tag, int resId);
}

/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasample.utils;

import android.support.annotation.UiThread;
import android.view.View;

@UiThread
public interface OnClickListener {

    void onItemClicked(int position, View view);
}

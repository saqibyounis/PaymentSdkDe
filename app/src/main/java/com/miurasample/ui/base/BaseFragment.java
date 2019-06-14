/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasample.ui.base;

import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;

public abstract class BaseFragment extends Fragment {

    private MaterialDialog progressDialog;

    protected abstract void presenterSetUp();

    public void showToast(String message, int duration) {
        Toast.makeText(getAppCombatActivity(), message, duration).show();
    }

    public void showToast(int stringRes, int duration) {
        showToast(getResources().getString(stringRes), duration);
    }

    public void showWorkingDialog(int resId) {
        progressDialog = new MaterialDialog.Builder(getAppCombatActivity())
                .content(getResources().getString(resId)).progress(true, 0).cancelable(false).show();
    }

    public void showWorkingDialog(String message) {
        progressDialog = new MaterialDialog.Builder(getAppCombatActivity())
                .content(message).progress(true, 0).cancelable(false).show();
    }

    public void hideWorkingDialog() {
        if (progressDialog != null) {
            progressDialog.dismiss();
        }
    }

    public AppCompatActivity getAppCombatActivity() {
        return (AppCompatActivity) getActivity();
    }
}
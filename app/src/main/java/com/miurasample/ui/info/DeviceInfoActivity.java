/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasample.ui.info;

import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.UiThread;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SwitchCompat;
import android.util.Pair;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.GravityEnum;
import com.afollestad.materialdialogs.MaterialDialog;
import com.miurasample.R;
import com.miurasample.module.bluetooth.BluetoothDeviceType;
import com.miurasample.module.bluetooth.BluetoothModule;
import com.miurasample.ui.base.BaseActivity;
import com.miurasystems.miuralibrary.api.objects.Capability;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import butterknife.Bind;
import butterknife.ButterKnife;

@UiThread
public class DeviceInfoActivity extends BaseActivity implements DeviceInfoPresenter.ViewDeviceInfo {

    private static String TAG = DeviceInfoActivity.class.getName();
    private DeviceInfoPresenter presenter;
    private ViewHolder viewHolder;
    private BluetoothDeviceType type;
    private BluetoothDevice bluetoothDevice;
    private ProgressDialog update;
    private ProgressDialog keyUpdate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_info);
        viewHolder = new ViewHolder(this);

        this.bluetoothDevice = BluetoothModule.getInstance().getSelectedBluetoothDevice();
        if (bluetoothDevice == null) {
            throw new IllegalStateException("bluetoothDevice is empty!");
        }

        type = BluetoothDeviceType.getByDeviceTypeByName(bluetoothDevice.getName());
        initClickable();
        presenterSetUp();
    }

    @Override
    protected void presenterSetUp() {
        presenter = new DeviceInfoPresenter(this);
        presenter.onLoad();
    }

    private void initClickable() {
        viewHolder.bShowLogs.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                presenter.onButtonLogClicked();
            }
        });
        viewHolder.bUpdateMpi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                presenter.onButtonUpdateMpiClicked();
            }
        });
        viewHolder.bUpdateOS.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                presenter.onButtonUpdateMpiOsClicked();
            }
        });
        viewHolder.bUpdateRpi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                presenter.onButtonUpdateRpiClicked();
            }
        });
        viewHolder.bUpdateRpiOs.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                presenter.onButtonUpdateRpiOsClicked();
            }
        });
        viewHolder.bUpdateConfigs.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                presenter.onButtonUpdateConfigsClicked();
            }
        });
        viewHolder.bKeyInject.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                presenter.onButtonKeyInjectionClicked();
            }
        });
        viewHolder.bCapabilities.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                presenter.onButtonShowCapabilitiesClicked();
            }
        });
        viewHolder.bSetDefault.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                presenter.onButtonSetDefaultClicked();
            }
        });
        viewHolder.bUnsetDefault.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                presenter.onButtonUnsetDefaultClicked();
            }
        });
        viewHolder.swipeRefreshLayout.setOnRefreshListener(
                new SwipeRefreshLayout.OnRefreshListener() {
                    @Override
                    public void onRefresh() {
                        presenter.onRefreshRequest();
                    }
                });
        viewHolder.bConfiguration.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                presenter.onButtonConfigurationClicked();
            }
        });
        viewHolder.bUpdateTime.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                presenter.onButtonUpdateTimeClicked();
            }
        });
        viewHolder.bPlayground.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                presenter.onButtonPlaygroundClicked();
            }
        });
        viewHolder.switchUpdateEnabled.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean enabled) {
                        presenter.onButtonSkipUpdateClicked(enabled);
                    }
                });
    }

    @Override
    public void updateToolbarTitle(String title) {
        setUpToolbar(title, true);
    }

    @Override
    public void showProgress() {
        viewHolder.swipeRefreshLayout.post(new Runnable() {
            @Override
            public void run() {
                viewHolder.swipeRefreshLayout.setRefreshing(true);
            }
        });
    }

    @Override
    public void hideProgress() {
        viewHolder.swipeRefreshLayout.post(new Runnable() {
            @Override
            public void run() {
                viewHolder.swipeRefreshLayout.setRefreshing(false);
            }
        });
    }

    @Override
    public void showFileTransferProgress(String what) {
        update = new ProgressDialog(this);
        update.setTitle("Transferring update for " + what);
        update.setMessage("Please wait, updating " + what + "...");
        update.setCanceledOnTouchOutside(false);
        update.setProgress(0);
        update.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        update.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // todo abstraction failure.
                        hideFileTransferProgress();
                        BluetoothModule.getInstance().closeSession();
                    }
                });
        update.show();
    }

    @Override
    public void setFileTransferProgress(int percent) {
        if (update != null) {
            update.setProgress(percent);
        }
    }

    @Override
    public void showPostTransferHardResetDialog() {
        hideFileTransferProgress();
        update = new ProgressDialog(this);
        update.setTitle("Rebooting device");
        update.setMessage("Please wait, rebooting device to complete the update.");
        update.setCanceledOnTouchOutside(false);
        update.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        update.setButton(DialogInterface.BUTTON_NEUTRAL, "Cancel",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        hideFileTransferProgress();
                    }
                });

        update.show();
    }

    @Override
    public void hideFileTransferProgress() {
        if (update != null) {
            update.dismiss();
            update = null;
        }
    }

    @Override
    public void hideConfigTransferProgress() {
        if (update != null) {
            update.dismiss();
            update = null;
        }
    }

    @Override
    public void hidePreparingKeyInjection() {
        if (keyUpdate != null) {
            keyUpdate.dismiss();
            keyUpdate = null;
        }
    }

    @Override
    public void showConnected() {
        Toast.makeText(this, "Connected", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void showConnectionError() {
        Toast.makeText(this, "Some communication error", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void showDeviceInfoList(ArrayList<Pair<String, String>> pairs) {

        DeviceInfoAdapter adapter = new DeviceInfoAdapter(this, pairs);
        RecyclerView recyclerView = viewHolder.rvDeviceInfo;

        RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(
                getApplicationContext());
        recyclerView.setLayoutManager(mLayoutManager);
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        recyclerView.setAdapter(adapter);
    }

    @Override
    public void updateUpdateVisibility(boolean mpi, boolean os, boolean rpi, boolean rpiOs) {
        int mpiVisible = mpi ? View.VISIBLE : View.GONE;
        int osVisible = os ? View.VISIBLE : View.GONE;
        int rpiVisible = rpi ? View.VISIBLE : View.GONE;
        int rpiOsVisible = rpiOs ? View.VISIBLE : View.GONE;
        viewHolder.bUpdateMpi.setVisibility(mpiVisible);
        viewHolder.bUpdateOS.setVisibility(osVisible);
        viewHolder.bUpdateRpi.setVisibility(rpiVisible);
        viewHolder.bUpdateRpiOs.setVisibility(rpiOsVisible);
    }

    @Override
    public void updateConfigVisibility(boolean config) {
        int configsVisible = config ? View.VISIBLE : View.GONE;
        viewHolder.bUpdateConfigs.setVisibility(configsVisible);
    }

    @Override
    public void setButtonUpdateClockVisibility(boolean visible) {
        int state = visible ? View.VISIBLE : View.GONE;
        viewHolder.bUpdateTime.setVisibility(state);
    }

    @Override
    public void showLogsRemovedMsg() {
        Toast.makeText(this, "Logs removed successfully", Toast.LENGTH_LONG).show();
    }

    @Override
    public void showMsgBluetoothSessionInterrupted() {
        Toast.makeText(this, "Bluetooth session interrupted", Toast.LENGTH_LONG).show();
    }

    public void showFileLoadingError(final String what) {
        Toast.makeText(this, what + " Cannot get file from assets", Toast.LENGTH_SHORT).show();
    }

    public void showKeyInjectionError(String errorMessage) {
        String text = "KeyInjection Failed: " + errorMessage;
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void showCapabilities(ArrayList<Capability> capabilities) {
        ArrayList<String> values = new ArrayList<>();

        for (Capability capability : capabilities) {
            String value = capability.getName();
            if (capability.getValue() != null) {
                value += " :    " + " (" + capability.getValue() + ")";
            }

            values.add(value);
        }

        new MaterialDialog.Builder(this).items(values)
                .title("Capabilities")
                .canceledOnTouchOutside(false)
                .titleGravity(GravityEnum.CENTER)
                .neutralText("Cancel")
                .show();
    }

    @Override
    public void setDefaultButtonVisibility(boolean shouldShowDefault) {
        if (shouldShowDefault) {
            viewHolder.bSetDefault.setVisibility(View.VISIBLE);
            viewHolder.bUnsetDefault.setVisibility(View.GONE);
        } else {
            viewHolder.bSetDefault.setVisibility(View.GONE);
            viewHolder.bUnsetDefault.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void showConfiguration(HashMap<String, String> configurations) {
        ArrayList<String> values = new ArrayList<>();

        for (Map.Entry<String, String> config : configurations.entrySet()) {
            String value = config.getKey() + " :    " + "(" + config.getValue() + ")";
            values.add(value);
        }

        new MaterialDialog.Builder(this).items(values)
                .title("Config Files")
                .titleGravity(GravityEnum.CENTER)
                .canceledOnTouchOutside(false)
                .neutralText("Cancel")
                .show();
    }

    @Override
    public void showMsgCannotConnect() {
        Toast.makeText(this, "Cannot connect to device", Toast.LENGTH_LONG).show();
    }

    public void hideConfigUpdateButtons() {
        if (type == BluetoothDeviceType.PED) {
            viewHolder.bUpdateConfigs.setVisibility(View.GONE);
        }
    }

    public void hideSoftwareUpdateButtons() {
        if (type == BluetoothDeviceType.PED) {
            viewHolder.bUpdateOS.setVisibility(View.GONE);
            viewHolder.bUpdateMpi.setVisibility(View.GONE);
            viewHolder.bUpdateRpi.setVisibility(View.GONE);
            viewHolder.bUpdateRpiOs.setVisibility(View.GONE);

        }

        if (type == BluetoothDeviceType.POS) {
            viewHolder.bUpdateRpi.setVisibility(View.GONE);
            viewHolder.bUpdateRpiOs.setVisibility(View.GONE);
            viewHolder.bUpdateOS.setVisibility(View.GONE);
            viewHolder.bUpdateMpi.setVisibility(View.GONE);
            viewHolder.bUpdateConfigs.setVisibility(View.GONE);
        }
    }

    @Override
    public void hideButtons() {
        viewHolder.llButtons.setVisibility(View.GONE);
        if (type == BluetoothDeviceType.POS) {
            viewHolder.bConfiguration.setVisibility(View.GONE);
            viewHolder.bKeyInject.setVisibility(View.GONE);
            viewHolder.bUpdateConfigs.setVisibility(View.GONE);
        }
    }

    @Override
    public void showButtons() {
        viewHolder.llButtons.setVisibility(View.VISIBLE);
    }

    public void showSoftwareUpdateFileLoadingError() {
        Toast.makeText(this, "Cannot get files", Toast.LENGTH_SHORT).show();
    }

    public void showConfigTransferProgress() {
        update = new ProgressDialog(this);
        update.setTitle("Config files have been updated");
        update.setMessage("Please wait, rebooting device to complete the update.");
        update.setCanceledOnTouchOutside(false);
        update.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        update.setButton(DialogInterface.BUTTON_NEUTRAL, "Cancel",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        BluetoothModule.getInstance().closeSession();
                    }
                });
        update.show();
        Toast.makeText(this, "Config files have been updated", Toast.LENGTH_SHORT).show();
    }

    public void showPreparingKeyInjectionMessage() {
        keyUpdate = new ProgressDialog(this);
        keyUpdate.setTitle("Preparing Key injection ");
        keyUpdate.setMessage("Please wait updating...");
        keyUpdate.setCanceledOnTouchOutside(false);
        keyUpdate.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        keyUpdate.setButton(DialogInterface.BUTTON_NEUTRAL, "Cancel",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        BluetoothModule.getInstance().closeSession();
                    }
                });
        keyUpdate.show();
    }

    public void showKeyInjectionSuccessMessage() {
        new MaterialDialog.Builder(this)
                .title("Success !")
                .content("Key Injection Was Successful")
                .titleGravity(GravityEnum.CENTER)
                .canceledOnTouchOutside(false)
                .contentColor(Color.BLACK)
                .contentGravity(GravityEnum.CENTER)
                .positiveText("OK")
                .onPositive(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                        dialog.dismiss();
                        presenterSetUp();
                    }
                })
                .show();
    }

    @UiThread
    static class ViewHolder {

        @Bind(R.id.device_info_rv_info)
        RecyclerView rvDeviceInfo;

        @Bind(R.id.device_info_skip_update)
        SwitchCompat switchUpdateEnabled;

        @Bind(R.id.device_info_b_update_mpi)
        Button bUpdateMpi;

        @Bind(R.id.device_info_b_update_os)
        Button bUpdateOS;

        @Bind(R.id.device_info_b_update_rpi)
        Button bUpdateRpi;

        @Bind(R.id.device_info_b_update_rpios)
        Button bUpdateRpiOs;

        @Bind(R.id.device_info_b_show_logs)
        Button bShowLogs;

        @Bind(R.id.update_config_file)
        Button bUpdateConfigs;

        @Bind(R.id.device_info_b_key_injection)
        Button bKeyInject;

        @Bind(R.id.device_info_b_show_capabilities)
        Button bCapabilities;

        @Bind(R.id.device_info_b_show_configuration)
        Button bConfiguration;

        @Bind(R.id.device_info_b_set_default)
        Button bSetDefault;

        @Bind(R.id.device_info_b_unset_default)
        Button bUnsetDefault;

        @Bind(R.id.device_info_b_update_time)
        Button bUpdateTime;

        @Bind(R.id.device_info_refresh)
        SwipeRefreshLayout swipeRefreshLayout;

        @Bind(R.id.device_info_ll_buttons)
        LinearLayout llButtons;

        @Bind(R.id.device_info_b_playground)
        Button bPlayground;

        public ViewHolder(Activity activity) {
            ButterKnife.bind(this, activity);
        }
    }
}

package com.miurasample.ui.payment.base;


import android.app.Activity;
import android.content.Intent;
import android.support.annotation.AnyThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;
import android.util.Log;
import android.widget.Toast;

import com.miurasample.core.MiuraApplication;
import com.miurasample.module.bluetooth.BluetoothModule;
import com.miurasample.ui.base.BaseActivity;
import com.miurasample.ui.base.BasePresenter;
import com.miurasample.ui.base.UiRunnable;
import com.miurasample.ui.payment.data.PaymentDataActivity;
import com.miurasample.ui.payment.signature.PaymentSignatureActivity;
import com.miurasample.ui.payment.summary.PaymentSummaryActivity;
import com.miurasystems.examples.transactions.EmvTransactionAsync;
import com.miurasystems.examples.transactions.EmvTransactionException;
import com.miurasystems.examples.transactions.EmvTransactionSummary;
import com.miurasystems.examples.transactions.EmvTransactionType;
import com.miurasystems.examples.transactions.MagSwipeSummary;
import com.miurasystems.examples.transactions.MagSwipeTransactionAsync;
import com.miurasystems.examples.transactions.MagSwipeTransactionException;
import com.miurasystems.examples.transactions.OnlinePinSummary;
import com.miurasystems.examples.transactions.PaymentMagType;
import com.miurasystems.examples.transactions.SignatureSummary;
import com.miurasystems.examples.transactions.StartTransactionInfo;
import com.miurasystems.miuralibrary.api.executor.MiuraManager;
import com.miurasystems.miuralibrary.api.listener.MiuraDefaultListener;
import com.miurasystems.miuralibrary.api.utils.DisplayTextUtils;
import com.miurasystems.miuralibrary.enums.DeviceStatus;
import com.miurasystems.miuralibrary.enums.OnlinePINError;
import com.miurasystems.miuralibrary.enums.TransactionResponse;
import com.miurasystems.miuralibrary.events.DeviceStatusChange;
import com.miurasystems.miuralibrary.events.MpiEventHandler;
import com.miurasystems.miuralibrary.events.MpiEvents;
import com.miurasystems.miuralibrary.tlv.CardData;

import java.util.Locale;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

public abstract class BaseTransactionPresenter
        <ActivityType extends BaseActivity & BaseTransactionView>
        extends BasePresenter<ActivityType> {

    private static final int REQUEST_SIGNATURE = 10001;
    private static final int REQUEST_SUMMARY = 10002;
    private static final String TAG = BaseTransactionPresenter.class.getSimpleName();
    private static final MiuraManager MIURA_MANAGER = MiuraManager.getInstance();
    private static final MpiEvents MPI_EVENTS = MIURA_MANAGER.getMpiEvents();
    private static final SignatureSummary SIGNATURE_FAILED = new SignatureSummary("");

    @NonNull
    private final StartTransactionInfo mStartInfo;

    @NonNull
    private final SynchronousQueue<SignatureSummary> mSignatureQueue;

    @NonNull
    private final MpiEventHandler<DeviceStatusChange> mDeviceStatusHandler =
            new MpiEventHandler<DeviceStatusChange>() {
                @Override
                public void handle(@NonNull DeviceStatusChange arg) {
                    DeviceStatus status = arg.deviceStatus;
                    final String text = arg.statusText;
                    Log.d(TAG, String.format(Locale.ENGLISH,
                            "Device status: '%s'. Text status %s", status.name(), text)
                    );
                    postOnUiThread(new UiRunnable<ActivityType>() {
                        @Override
                        public void runOnUiThread(@NonNull ActivityType view) {
                            view.updateStatus("Device status changed: " + text);
                        }
                    });
                }
            };

    private final MpiEventHandler<CardData> mCardEventHandler = new MpiEventHandler<CardData>() {
        @Override
        public void handle(final @NonNull CardData cardData) {
            postOnUiThread(new UiRunnable<ActivityType>() {
                @Override
                public void runOnUiThread(@NonNull ActivityType view) {
                    handleCardEventOnUiThread(view, cardData);
                }
            });
        }
    };

    @Nullable
    private EmvTransactionAsync mEmvTransactionAsync;

    @Nullable
    private MagSwipeTransactionAsync mMagSwipeTransaction;

    @UiThread
    public BaseTransactionPresenter(
            @NonNull ActivityType view,
            @NonNull StartTransactionInfo startInfo
    ) {
        super(view);
        mStartInfo = startInfo;
        mSignatureQueue = new SynchronousQueue<>();
    }

    @UiThread
    public abstract void onLoad();

    @NonNull
    protected StartTransactionInfo getStartInfo() {
        return mStartInfo;
    }

    @AnyThread
    protected void registerEventHandlers() {
        MPI_EVENTS.CardStatusChanged.register(mCardEventHandler);
        MPI_EVENTS.DeviceStatusChanged.register(mDeviceStatusHandler);
    }

    @AnyThread
    protected void deregisterEventHandlers() {
        MPI_EVENTS.CardStatusChanged.deregister(mCardEventHandler);
        MPI_EVENTS.DeviceStatusChanged.deregister(mDeviceStatusHandler);
    }

    @UiThread
    protected abstract void handleCardEventOnUiThread(ActivityType view, CardData cardData);

    @UiThread
    protected void startEmvTransaction(EmvTransactionType emvTransactionType) {

        getView().updateStatus("Card ok, starting transaction");

        abortEmvTransactionAsync(null);
        abortSwipeTransactionAsync(null);

        EmvTransactionAsync emvTransactionAsync = new EmvTransactionAsync(
                MIURA_MANAGER, emvTransactionType
        );
        mEmvTransactionAsync = emvTransactionAsync;

        emvTransactionAsync.startTransactionAsync(
                mStartInfo.mAmountInPennies,
                MiuraApplication.currencyCode.getValue(),
                new EmvTransactionAsync.Callback() {
                    @Override
                    public void publishStartTransactionResult(@NonNull final String response) {
                        postOnUiThread(new UiRunnable<ActivityType>() {
                            @Override
                            public void runOnUiThread(@NonNull ActivityType view) {
                                view.updateStatus("Start transaction success");
                                view.showStartTransactionData(response);
                            }
                        });
                    }

                    @Override
                    public void onSuccess(@NonNull final EmvTransactionSummary result) {
                        postOnUiThread(new UiRunnable<ActivityType>() {
                            @Override
                            public void runOnUiThread(@NonNull ActivityType view) {
                                view.updateStatus("Continue transaction success");
                                startSummary(result);
                            }
                        });
                    }

                    @Override
                    public void onError(@NonNull EmvTransactionException exception) {
                        TransactionResponse response = exception.mErrCode;
                        String explanation = exception.getMessage();

                        Log.d(TAG, String.format("onError(%s, %s)", response, explanation));

                        String text;
                        if (exception.getMessage() == null) {
                            text = "Transaction error";
                        } else {
                            text = explanation;
                        }

                        if (response != TransactionResponse.UNKNOWN) {
                            text += ": " + response;
                        }

                        final String finalText = text;
                        postOnUiThread(new UiRunnable<ActivityType>() {
                            @Override
                            public void runOnUiThread(@NonNull ActivityType view) {
                                view.updateStatus(finalText);
                            }
                        });
                    }
                });
    }

    @UiThread
    protected void startSwipeTransaction(
            MagSwipeSummary magSwipeSummary,
            PaymentMagType paymentMagType
    ) {
        Log.d(TAG, "startSwipeTransaction");

        abortEmvTransactionAsync(null);
        abortSwipeTransactionAsync(null);

        mMagSwipeTransaction = new MagSwipeTransactionAsync(MIURA_MANAGER, paymentMagType);
        mMagSwipeTransaction.startTransactionAsync(
                magSwipeSummary,
                mStartInfo.mAmountInPennies,
                MiuraApplication.currencyCode.getValue(),
                new MagSwipeTransactionAsync.Callback() {
                    @NonNull
                    @Override
                    public SignatureSummary getSignatureFromUser()
                            throws MagSwipeTransactionException {
                        return waitForSignatureActivity();
                    }

                    @Override
                    public void onPinSuccess(
                            @NonNull final MagSwipeSummary magSwipeSummary,
                            @NonNull final OnlinePinSummary onlinePinSummary
                    ) {
                        postOnUiThread(new UiRunnable<ActivityType>() {
                            @Override
                            public void runOnUiThread(@NonNull ActivityType view) {
                                view.updateStatus(
                                        "Online PIN success, PIN block: " +
                                                onlinePinSummary.mPinData + "\nKSN: "
                                                + onlinePinSummary.mPinKSN
                                );
                                startSummary(magSwipeSummary, onlinePinSummary);
                            }
                        });
                    }

                    @Override
                    public void onSignatureSuccess(
                            @NonNull final MagSwipeSummary magSwipeSummary,
                            @NonNull final SignatureSummary signature
                    ) {
                        postOnUiThread(new UiRunnable<ActivityType>() {
                            @Override
                            public void runOnUiThread(@NonNull ActivityType view) {
                                startSummary(magSwipeSummary, signature);
                            }
                        });
                    }

                    @Override
                    public void onError(
                            final @NonNull MagSwipeTransactionException exception
                    ) {
                        postOnUiThread(new UiRunnable<ActivityType>() {
                            @Override
                            public void runOnUiThread(@NonNull ActivityType view) {
                                if (exception.mErrorCode == null) {
                                    getView().updateStatus(
                                            "Transaction Error: " + exception.getMessage());
                                } else {
                                    OnlinePINError error = exception.mErrorCode;
                                    if (OnlinePINError.NO_PIN_KEY == error) {
                                        getView().updateStatus(
                                                "Online PIN error: No PIN key installed.");
                                    } else {
                                        if (OnlinePINError.INVALID_PARAM == error) {
                                            Log.d(TAG, "Invalid parameter sent "
                                                    + "to online PIN command.");
                                        }
                                        getView().updateStatus(
                                                "Online PIN error: Error performing online PIN. "
                                                        + "Retrieve log from PED.");
                                    }
                                }
                            }
                        });
                    }
                });
    }

    @UiThread
    public void onContinueTransactionClicked() {
        Log.d(TAG, "onContinueTransactionClicked");
        if (mEmvTransactionAsync == null) {
            closeView();
        } else {
            mEmvTransactionAsync.continueTransactionAsync();
        }
    }

    @UiThread
    public void onCancelShowStartTransactionData() {
        // todo Is this a good way to do it?
        onLoad();
    }

    @UiThread
    public void onCancelButtonClicked() {
        // todo need to reset the view properly?
        boolean isChip = mEmvTransactionAsync != null;
        boolean isSwipe = mMagSwipeTransaction != null;
        if (isChip && isSwipe) {
            Log.w(TAG, "Both chip and swipe transactions outstanding?");
            closeView();
            return;
        } else if (!isChip && !isSwipe) {
            closeView();
            return;
        }

        // We have to wait for the aborts to finish to closeView, otherwise we can't be sure
        // the transaction and abort methods aren't preventing the connection from closing.
        MiuraDefaultListener listener = new MiuraDefaultListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Abort Success");
                postOnUiThread(new UiRunnable<ActivityType>() {
                    @Override
                    public void runOnUiThread(@NonNull ActivityType view) {
                        closeView();
                    }
                });
            }

            @Override
            public void onError() {
                Log.d(TAG, "Abort Failed");
                postOnUiThread(new UiRunnable<ActivityType>() {
                    @Override
                    public void runOnUiThread(@NonNull ActivityType view) {
                        closeView();
                    }
                });
            }
        };
        if (isChip) {
            abortEmvTransactionAsync(listener);
        } else {
            abortSwipeTransactionAsync(listener);
        }
    }

    @UiThread
    private void abortEmvTransactionAsync(@Nullable MiuraDefaultListener listener) {
        Log.d(TAG, "abortEmvTransactionAsync");

        if (mEmvTransactionAsync == null) {
            Log.d(TAG, "abortEmvTransactionAsync: mEmvTransactionAsync == null");
            return;
        }
        mEmvTransactionAsync.abortTransactionAsync(listener);
        mEmvTransactionAsync = null;
    }

    @UiThread
    private void abortSwipeTransactionAsync(@Nullable MiuraDefaultListener listener) {
        Log.d(TAG, "abortSwipeTransactionAsync");

        if (mMagSwipeTransaction == null) {
            Log.d(TAG, "abortSwipeTransaction: mMagSwipeTransaction == null");
            return;
        }
        mMagSwipeTransaction.abortTransactionAsync(listener);
        mMagSwipeTransaction = null;
    }

    @UiThread
    private void closeView() {
        deregisterEventHandlers();

        Intent i = new Intent(getView(), PaymentDataActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        getView().startActivity(i);
        getView().finish();
        BluetoothModule.getInstance().closeSession();

        Toast.makeText(getView(), "Transaction Canceled", Toast.LENGTH_LONG).show();
    }

    @UiThread
    private void startSummary(MagSwipeSummary magSwipeSummary, OnlinePinSummary onlinePinSummary) {
        deregisterEventHandlers();
        BluetoothModule.getInstance().closeSession();

        mMagSwipeTransaction = null;

        Intent intent = PaymentSummaryActivity.makeIntent(
                getView(), mStartInfo, magSwipeSummary, onlinePinSummary
        );
        getView().startActivityForResult(intent, REQUEST_SUMMARY);
    }

    @UiThread
    private void startSummary(MagSwipeSummary magSwipeSummary, SignatureSummary signatureSummary) {
        deregisterEventHandlers();
        BluetoothModule.getInstance().closeSession();

        mMagSwipeTransaction = null;

        Intent intent = PaymentSummaryActivity.makeIntent(
                getView(), mStartInfo, magSwipeSummary, signatureSummary
        );
        getView().startActivityForResult(intent, REQUEST_SUMMARY);
    }

    @UiThread
    private void startSummary(EmvTransactionSummary chipSummary) {
        deregisterEventHandlers();
        BluetoothModule.getInstance().closeSession();

        mEmvTransactionAsync = null;

        Intent intent = PaymentSummaryActivity.makeIntent(getView(), mStartInfo, chipSummary);
        getView().startActivityForResult(intent, REQUEST_SUMMARY);
    }

    @NonNull
    @WorkerThread
    private SignatureSummary waitForSignatureActivity() throws MagSwipeTransactionException {
        Log.d(TAG, "startSign");

        // Start the signing view up and then wait for it to respond via mSignatureQueue.

        postOnUiThreadDelayed(new UiRunnable<ActivityType>() {
            @Override
            public void runOnUiThread(@NonNull ActivityType view) {
                Intent intent = new Intent(view, PaymentSignatureActivity.class);
                view.startActivityForResult(intent, REQUEST_SIGNATURE);
            }
        }, 1000L);

        /*
            We _could_ have a timeout here, but don't have a good way to
            stop the runnable and cancel the activity? So just wait.
            We'd need to change the signature activity to look for a flag or something?
            `mHandler.getLooper().getThread().interrupt();` ?
        */

        SignatureSummary summary;
        try {
            summary = mSignatureQueue.poll(15L, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new MagSwipeTransactionException("Signature poll interrupted?", e);
        }
        if (summary == null) {
            throw new MagSwipeTransactionException("Signature poll timed out");
        } else if (SIGNATURE_FAILED.equals(summary)) {
            throw new MagSwipeTransactionException("Signature error");
        }

        return summary;
    }

    @UiThread
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        deregisterEventHandlers();

        if (requestCode == REQUEST_SIGNATURE) {
            final SignatureSummary signatureSummary;

            if (resultCode == Activity.RESULT_OK) {
                String bitmapBase64 = data.getStringExtra("bitmap");
                signatureSummary = new SignatureSummary(bitmapBase64);

                getView().updateStatus("Signature OK, ready to use");
            } else {
                //user cancelled screen, show error
                getView().updateStatus("There is no signature");
                signatureSummary = SIGNATURE_FAILED;
            }

            try {
                mSignatureQueue.offer(signatureSummary, 15L, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
                getView().updateStatus("Failed to post signature to queue");
            }

        } else if (requestCode == REQUEST_SUMMARY) {
            getView().setResult(Activity.RESULT_OK);
            getView().finish();
        }
    }

    protected static void showTextOnDevice(String text) {
        if (BluetoothModule.getInstance().isSessionOpen()) {
            MIURA_MANAGER.displayText(DisplayTextUtils.getCenteredText(text), null);
        }
    }


}

/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary;


import android.support.annotation.NonNull;

public abstract class Result<ReturnType, ErrorType> {
    private final boolean mSuccess;

    Result(boolean success) {
        mSuccess = success;
    }

    public final boolean isSuccess() {
        return mSuccess;
    }

    public final boolean isError() {
        return !mSuccess;
    }

    public final Success<ReturnType, ErrorType> asSuccess() {
        if (!mSuccess) {
            throw new IllegalStateException("Not a success");
        }
        return (Success<ReturnType, ErrorType>) this;
    }

    public final Error<ReturnType, ErrorType> asError() {
        if (mSuccess) {
            throw new IllegalStateException("Not an error");
        }
        return (Error<ReturnType, ErrorType>) this;
    }

    public static final class Success<ReturnType, ErrorType> extends Result<ReturnType, ErrorType> {
        @NonNull
        private final ReturnType mValue;

        public Success(@NonNull ReturnType value) {
            super(true);
            this.mValue = value;
        }

        @NonNull
        public ReturnType getValue() {
            return mValue;
        }
    }

    public static final class Error<ReturnType, ErrorType> extends Result<ReturnType, ErrorType> {
        @NonNull
        private final ErrorType mError;

        public Error(@NonNull ErrorType err) {
            super(false);
            this.mError = err;
        }

        @NonNull
        public ErrorType getError() {
            return mError;
        }
    }
}

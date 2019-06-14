/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.events;


import android.support.annotation.NonNull;

/**
 * Event listener used by MpiEventPublisher and MpiEvents
 * @param <Event> Event handler argument type
 */
public interface MpiEventHandler<Event> {
    void handle(@NonNull Event arg);
}

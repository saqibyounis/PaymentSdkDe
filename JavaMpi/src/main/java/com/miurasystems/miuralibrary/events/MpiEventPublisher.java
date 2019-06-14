/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.events;

import android.support.annotation.NonNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ConcurrentModificationException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;


/**
 * @param <Event> Event handler argument type
 */
public class MpiEventPublisher<Event> {

    /** SLF4J Logger */
    private static final Logger LOGGER = LoggerFactory.getLogger(MpiEventPublisher.class);

    /**
     * The event handler that is published to when {@link #notifyListener(Event)} is called
     *
     * <p> There is only a single event handler, and it might be a null reference (i.e. no
     * handler is currently registered).
     *
     * <p> It's atomic as it doesn't bear a direct relation to {@link #mGroupLock}, and
     * having its own lock is overkill.
     */
    @NonNull
    private final AtomicReference<MpiEventHandler<Event>> mHandler = new AtomicReference<>(null);

    /**
     * Prevents multiple threads from entering different event listeners at the same time.
     *
     * <p>
     * All MpiEventPublishers sharing the same mGroupLock are said to be in the same 'group'.
     * </p>
     */
    @NonNull
    private final ReentrantLock mGroupLock;

    /**
     * Create a new MpiEventPublishers in its own group
     */
    MpiEventPublisher() {
        this(new ReentrantLock());
    }

    /**
     * Create a new MpiEventPublishers, in the same group as groupLock
     *
     * @param groupLock The lock to use to get exclusive access to the group's listeners.
     */
    MpiEventPublisher(@NonNull ReentrantLock groupLock) {
        mGroupLock = groupLock;
    }

    /**
     * Add a MpiEventHandler to this publisher
     *
     * <p>The handler will be called whenever ({@link #notifyListener(Object)} is called
     *
     * <p>There is currently only a single handler. If you wish to replace the current handler
     * it is <b>recommended</b> that you deregister your previous one first,
     * though it is not required to do so.
     * </p>
     *
     * <pre>
     * {@code
     * events.Whatever.register(handlerA);
     * events.Whatever.register(handlerB);
     * }
     * </pre>
     *
     * <p>will work fine, though it will log warnings about the trampling of handlerA.</p>
     *
     * <p>It is fine to call register from {@link MpiEventHandler#handle(Event)}
     *
     * @param handler The handler to register for events
     * @return true
     */
    public boolean register(@NonNull MpiEventHandler<Event> handler) {
        MpiEventHandler<Event> oldValue = mHandler.getAndSet(handler);
        if (oldValue != null) {
            LOGGER.debug("register: Handler '{}' overwrote handler '{}'?", handler, oldValue);
        }
        return true;
    }

    /**
     * Remove an MpiEventHandler from this publisher, <b>if</b> it's currently set.
     *
     * <p>If handler is the current event handler then it is removed, there is now no event handler
     * registered with this publisher, and true is returned.
     * If the the handler is <b>not</b> the current handler then the
     * current handler is left inplace, an error is logged, and false is returned.
     *
     * <p>It is fine to call deregister from {@link MpiEventHandler#handle(Event)}
     *
     * @param handler The handler to remove
     * @return true if {@code handler} was the current handler and it was removed,
     * false if the current handler was left in-place.
     */
    public boolean deregister(@NonNull MpiEventHandler<Event> handler) {
        boolean ok = mHandler.compareAndSet(handler, null);
        if (!ok) {
            LOGGER.debug("deregister: Tried to deregister handler '{}' that wasn't set? "
                            + "Leaving as '{}' (possibly)",
                    handler, mHandler.get());
        }
        return ok;
    }

    /**
     * Notifies the currently registered event handler of the given event.
     *
     * <p>
     * Do not call this from a listener being notified by a publisher that is in the same
     * group as this publisher. (This also includes recursive notifyListener/handle calls)
     * </p>
     *
     * @param arg The event argument to pass on to {@link MpiEventHandler#handle(Event)}.
     * @return true if a handler was subscribed and notified, false if there was no current handler
     */
    boolean notifyListener(@NonNull Event arg) {

        boolean alreadyInNotify = mGroupLock.isHeldByCurrentThread();
        if (alreadyInNotify) {
            // We don't want two handlers in the same group being called at once from different
            // threads. mGroupLock prevents this from happening on different threads.
            //
            // For consistences sake, don't allow the current thread to be in more than one
            // listener at once as well. i.e if this thread has a naughty listener
            // that is trying to call something else in the group, we'll already hold the lock.
            // So tell them off.
            String s = "Can't call .notifyListener() from a listener";
            throw new ConcurrentModificationException(s);
        }

        mGroupLock.lock();
        try {
            MpiEventHandler<Event> handler = this.mHandler.get();
            if (handler == null) {
                return false;
            }
            // Don't try and catch errors. As a library it's not our responsibility to
            // try and save the app from crashing due to their own dodgy code, so
            // let the error propagate so that the app becomes aware of the problem.
            // But ensure that we do any necessary cleanup.
            handler.handle(arg);
        } finally {
            mGroupLock.unlock();
        }
        return true;
    }
}

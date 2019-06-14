/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.events;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

import android.support.annotation.NonNull;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class MpiEventPublisherTest {

    /**
     * Ensure that a listener actually notifies the registered handler
     */
    @Test
    public void simple() {
        // setup
        // -----------------------------------------------------------------------------------
        MpiEventPublisher<String> stringPublisher = new MpiEventPublisher<>();

        final List<String> arguments = new ArrayList<>(5);
        MpiEventHandler<String> handler = new MpiEventHandler<String>() {
            @Override
            public void handle(@NonNull String arg) {
                arguments.add(arg);
            }
        };

        // execute
        // -----------------------------------------------------------------------------------
        boolean ok;
        ok = stringPublisher.register(handler);
        ok &= stringPublisher.notifyListener("Hello");
        ok &= stringPublisher.notifyListener("World!");
        ok &= stringPublisher.notifyListener("How");
        ok &= stringPublisher.notifyListener("are");
        ok &= stringPublisher.notifyListener("you?");
        ok &= stringPublisher.deregister(handler);

        // verify
        // -----------------------------------------------------------------------------------
        assertThat(join(" ", arguments), is(equalTo("Hello World! How are you?")));
        assertThat(ok, is(true));
    }

    /**
     * Ensure that a call to an unregistered listener doesn't explode.
     */
    @Test
    public void unregistered() {
        // setup
        // -----------------------------------------------------------------------------------
        MpiEventPublisher<String> stringPublisher = new MpiEventPublisher<>();
        final List<String> arguments = new ArrayList<>(5);
        MpiEventHandler<String> handler = new MpiEventHandler<String>() {
            @Override
            public void handle(@NonNull String arg) {
                arguments.add(arg);
            }
        };

        // execute
        // -----------------------------------------------------------------------------------
        boolean notifiedBefore;
        notifiedBefore = stringPublisher.notifyListener("Hello");
        notifiedBefore |= stringPublisher.notifyListener("World!");

        boolean ok;
        ok = stringPublisher.register(handler);
        ok &= stringPublisher.notifyListener("How");
        ok &= stringPublisher.notifyListener("are");
        ok &= stringPublisher.notifyListener("you?");
        ok &= stringPublisher.deregister(handler);

        // verify
        // -----------------------------------------------------------------------------------
        assertThat(join(" ", arguments), is(equalTo("How are you?")));
        assertThat(notifiedBefore, is(false));
        assertThat(ok, is(true));
    }

    /**
     * Ensure that deregister actually works
     */
    @Test
    public void deregister() {
        // setup
        // -----------------------------------------------------------------------------------
        MpiEventPublisher<String> stringPublisher = new MpiEventPublisher<>();
        final List<String> arguments = new ArrayList<>(1);
        MpiEventHandler<String> handler = new MpiEventHandler<String>() {
            @Override
            public void handle(@NonNull String arg) {
                arguments.add(arg);
            }
        };

        // execute
        // -----------------------------------------------------------------------------------
        boolean ok;
        ok = stringPublisher.register(handler);
        ok &= stringPublisher.notifyListener("Hello");
        ok &= stringPublisher.deregister(handler);
        boolean afterDeregister = stringPublisher.notifyListener("World!");

        // verify
        // -----------------------------------------------------------------------------------
        assertThat(join(" ", arguments), is(equalTo("Hello")));
        assertThat(ok, is(true));
        assertThat(afterDeregister, is(false));
    }

    /**
     * Ensure that a listener can deregister itself
     */
    @Test
    public void deregisterSelf() {
        // setup
        // -----------------------------------------------------------------------------------
        final MpiEventPublisher<String> stringPublisher = new MpiEventPublisher<>();
        final List<String> arguments = new ArrayList<>(2);
        MpiEventHandler<String> handler = new MpiEventHandler<String>() {
            @Override
            public void handle(@NonNull String arg) {
                arguments.add(arg);
                stringPublisher.deregister(this);
            }
        };

        // execute
        // -----------------------------------------------------------------------------------
        boolean ok;
        ok = stringPublisher.register(handler);
        ok &= stringPublisher.notifyListener("Hello");
        // stringPublisher.deregister(handler); is done by listener
        boolean afterDeregister = stringPublisher.notifyListener("World!");

        // verify
        // -----------------------------------------------------------------------------------
        assertThat(join(" ", arguments), is(equalTo("Hello")));
        assertThat(ok, is(true));
        assertThat(afterDeregister, is(false));
    }

    /**
     * Tests that we can add listeners to another publisher from inside listeners.
     */
    @Test
    public void registerAnother() {
        // setup
        // -----------------------------------------------------------------------------------
        final MpiEventPublisher<String> stringPublisher = new MpiEventPublisher<>();
        final MpiEventPublisher<ConnectionInfo> connectedPublisher = new MpiEventPublisher<>();
        final List<String> arguments = new ArrayList<>(5);
        final MpiEventHandler<String> stringHandler = new MpiEventHandler<String>() {
            @Override
            public void handle(@NonNull String arg) {
                arguments.add(arg);
            }
        };
        final MpiEventHandler<ConnectionInfo> connectHandler =
                new MpiEventHandler<ConnectionInfo>() {
                    @Override
                    public void handle(@NonNull ConnectionInfo arg) {
                        stringPublisher.register(stringHandler);
                    }
                };

        // execute
        // -----------------------------------------------------------------------------------
        boolean ok;
        ok = connectedPublisher.register(connectHandler);
        ok &= connectedPublisher.notifyListener(new ConnectionInfo(true));

        // stringPublisher registered by connectedPublisher
        ok &= stringPublisher.notifyListener("Hello");
        ok &= stringPublisher.notifyListener("World!");

        ok &= connectedPublisher.deregister(connectHandler);
        ok &= stringPublisher.deregister(stringHandler);

        // verify
        // -----------------------------------------------------------------------------------
        assertThat(join(" ", arguments), is(equalTo("Hello World!")));
        assertThat(ok, is(true));
    }

    /**
     * Ensure that a listener can deregister a listener from another publisher
     */
    @Test
    public void deregisterAnother() {
        // setup
        // -----------------------------------------------------------------------------------
        final MpiEventPublisher<String> stringPublisher = new MpiEventPublisher<>();
        final MpiEventPublisher<ConnectionInfo> connectedPublisher = new MpiEventPublisher<>();
        final MpiEventPublisher<ConnectionInfo> disconnectedPublisher = new MpiEventPublisher<>();

        final List<String> arguments = new ArrayList<>(5);
        final MpiEventHandler<String> stringHandler = new MpiEventHandler<String>() {
            @Override
            public void handle(@NonNull String arg) {
                arguments.add(arg);
            }
        };

        final MpiEventHandler<ConnectionInfo> connectHandler =
                new MpiEventHandler<ConnectionInfo>() {
                    @Override
                    public void handle(@NonNull ConnectionInfo arg) {
                        connectedPublisher.deregister(this);
                        stringPublisher.register(stringHandler);
                    }
                };
        final MpiEventHandler<ConnectionInfo> disconnectHandler =
                new MpiEventHandler<ConnectionInfo>() {
                    @Override
                    public void handle(@NonNull ConnectionInfo arg) {
                        stringPublisher.deregister(stringHandler);
                        disconnectedPublisher.deregister(this);
                    }
                };

        connectedPublisher.register(connectHandler);
        disconnectedPublisher.register(disconnectHandler);

        // execute
        // -----------------------------------------------------------------------------------
        boolean ok;
        ok = connectedPublisher.notifyListener(new ConnectionInfo(true));
        ok &= stringPublisher.notifyListener("Hello");
        ok &= stringPublisher.notifyListener("World!");
        ok &= disconnectedPublisher.notifyListener(new ConnectionInfo(false));

        // Everything should be unregistered now, so the following will have no effect
        boolean afterDeregister;
        afterDeregister = connectedPublisher.notifyListener(new ConnectionInfo(true));
        afterDeregister &= stringPublisher.notifyListener("blah!");
        afterDeregister &= disconnectedPublisher.notifyListener(new ConnectionInfo(false));

        // verify
        // -----------------------------------------------------------------------------------
        assertThat(join(" ", arguments), is(equalTo("Hello World!")));
        assertThat(ok, is(true));
        assertThat(afterDeregister, is(false));
    }

    /**
     * Test that we can overwrite a listener without deregistering another one first
     * and, whilst it works, that the appropriate errors are given
     */
    @Test
    public void overwrite() {
        // setup
        // -----------------------------------------------------------------------------------
        MpiEventPublisher<String> stringPublisher = new MpiEventPublisher<>();

        final List<String> argumentsA = new ArrayList<>(5);
        MpiEventHandler<String> handlerA = new MpiEventHandler<String>() {
            @Override
            public void handle(@NonNull String arg) {
                argumentsA.add(arg);
            }
        };

        final List<String> argumentsB = new ArrayList<>(5);
        MpiEventHandler<String> handlerB = new MpiEventHandler<String>() {
            @Override
            public void handle(@NonNull String arg) {
                argumentsB.add(arg);
            }
        };

        // execute
        // -----------------------------------------------------------------------------------
        boolean ok;
        ok = stringPublisher.register(handlerA);
        ok &= stringPublisher.notifyListener("Hello");
        ok &= stringPublisher.notifyListener("World!");
        // return value is true as technically we registered ok
        ok &= stringPublisher.register(handlerB);
        ok &= stringPublisher.notifyListener("How");
        ok &= stringPublisher.notifyListener("are");
        ok &= stringPublisher.notifyListener("you?");
        ok &= stringPublisher.deregister(handlerB);

        // verify
        // -----------------------------------------------------------------------------------
        assertThat(join(" ", argumentsA), is(equalTo("Hello World!")));
        assertThat(join(" ", argumentsB), is(equalTo("How are you?")));
        assertThat(ok, is(true));
    }

    /**
     * Test that if we try to deregister a listener that isn't set, we get the appropriate error
     */
    @Test
    public void deregisterNotSet() {
        // setup
        // -----------------------------------------------------------------------------------
        MpiEventPublisher<String> stringPublisher = new MpiEventPublisher<>();

        final List<String> argumentsA = new ArrayList<>(5);
        MpiEventHandler<String> handlerA = new MpiEventHandler<String>() {
            @Override
            public void handle(@NonNull String arg) {
                argumentsA.add(arg);
            }
        };

        MpiEventHandler<String> handlerB = new MpiEventHandler<String>() {
            @Override
            public void handle(@NonNull String arg) {
            }
        };

        // execute
        // -----------------------------------------------------------------------------------

        boolean beforeRegister = stringPublisher.deregister(handlerA);
        beforeRegister |= stringPublisher.deregister(handlerB);

        boolean ok = stringPublisher.register(handlerA);
        ok &= stringPublisher.notifyListener("Hello");
        ok &= stringPublisher.notifyListener("World!");

        boolean wrongDeregister = stringPublisher.deregister(handlerB);
        ok &= stringPublisher.notifyListener("How");
        ok &= stringPublisher.notifyListener("are");
        ok &= stringPublisher.notifyListener("you?");

        // now actually remove handlerA, and then try and remove it again (which should fail)
        ok &= stringPublisher.deregister(handlerA);
        wrongDeregister |= stringPublisher.deregister(handlerA);

        // verify
        // -----------------------------------------------------------------------------------
        assertThat(join(" ", argumentsA), is(equalTo("Hello World! How are you?")));
        assertThat(ok, is(true));
        assertThat(beforeRegister, is(false));
        assertThat(wrongDeregister, is(false));
    }

    /**
     * There's no reason the same listener can't be used on multiple publishers
     */
    @Test(timeout = 300)
    public void sameListenerDifferentPublishersDifferentGroups() {
        // setup
        // -----------------------------------------------------------------------------------
        final MpiEventPublisher<String> firstPublisher = new MpiEventPublisher<>();
        final MpiEventPublisher<String> secondPublisher = new MpiEventPublisher<>();
        final List<String> arguments = new ArrayList<>(5);
        MpiEventHandler<String> handler = new MpiEventHandler<String>() {
            @Override
            public void handle(@NonNull String arg) {
                arguments.add(arg);
            }
        };

        // execute
        // -----------------------------------------------------------------------------------
        boolean ok = firstPublisher.register(handler);
        ok &= secondPublisher.register(handler);

        ok &= firstPublisher.notifyListener("Hello");
        ok &= firstPublisher.notifyListener("World!");
        ok &= firstPublisher.deregister(handler);

        ok &= secondPublisher.notifyListener("How is it going?");
        ok &= secondPublisher.deregister(handler);

        // verify
        // -----------------------------------------------------------------------------------
        assertThat(join(" ", arguments), is(equalTo("Hello World! How is it going?")));
        assertThat(ok, is(true));
    }

    /**
     * Ensure that a listener can't call its own notify.
     */
    @Test(timeout = 300)
    public void selfNotify() {
        // setup
        // -----------------------------------------------------------------------------------
        final MpiEventPublisher<String> stringPublisher = new MpiEventPublisher<>();
        MpiEventHandler<String> handler = new MpiEventHandler<String>() {
            @Override
            public void handle(@NonNull String arg) {
                stringPublisher.notifyListener(arg);
            }
        };

        // execute
        // -----------------------------------------------------------------------------------
        boolean ok = stringPublisher.register(handler);
        try {
            stringPublisher.notifyListener("Hello");
            Assert.fail();
        } catch (ConcurrentModificationException ignore) {
        }
        ok &= stringPublisher.deregister(handler);

        // verify
        // -----------------------------------------------------------------------------------
        assertThat(ok, is(true));
    }

    /**
     * Ensure that a listener can't call its own notify, even if it's in a group
     */
    @Test(timeout = 300)
    public void selfNotifyInGroup() {
        // setup
        // -----------------------------------------------------------------------------------
        ReentrantLock groupLock = new ReentrantLock(true);
        final MpiEventPublisher<String> stringPublisher = new MpiEventPublisher<>(groupLock);
        final MpiEventPublisher<String> anotherPublisher = new MpiEventPublisher<>(groupLock);
        MpiEventHandler<String> handler = new MpiEventHandler<String>() {
            @Override
            public void handle(@NonNull String arg) {
                stringPublisher.notifyListener(arg);
            }
        };

        // execute
        // -----------------------------------------------------------------------------------
        boolean ok = stringPublisher.register(handler);
        try {
            stringPublisher.notifyListener("Hello");
            Assert.fail();
        } catch (ConcurrentModificationException ignore) {
        }
        ok &= stringPublisher.deregister(handler);

        // verify
        // -----------------------------------------------------------------------------------
        assertThat(ok, is(true));
        // useless, but keeps reference alive.
        assertThat(anotherPublisher, is(notNullValue()));
    }

    /**
     * Ensure that a listener can't call notify() for another listener protected by the same lock
     */
    @Test(timeout = 300)
    public void sharedNotifyLock() {
        // setup
        // -----------------------------------------------------------------------------------
        ReentrantLock groupLock = new ReentrantLock(true);
        final MpiEventPublisher<String> firstPublisher = new MpiEventPublisher<>(groupLock);
        final MpiEventPublisher<String> secondPublisher = new MpiEventPublisher<>(groupLock);
        MpiEventHandler<String> firstHandler = new MpiEventHandler<String>() {
            @Override
            public void handle(@NonNull String arg) {
                secondPublisher.notifyListener(arg);
            }
        };
        MpiEventHandler<String> secondHandler = new MpiEventHandler<String>() {
            @Override
            public void handle(@NonNull String arg) {
                Assert.fail("Should never have gotten into second event handler");
            }
        };

        // execute
        // -----------------------------------------------------------------------------------
        boolean ok = firstPublisher.register(firstHandler);
        ok &= secondPublisher.register(secondHandler);

        try {
            firstPublisher.notifyListener("Hello");
            Assert.fail("Expected ConcurrentModificationException");
        } catch (ConcurrentModificationException ignore) {
        }
        ok &= firstPublisher.deregister(firstHandler);
        ok &= secondPublisher.deregister(secondHandler);

        // verify
        // -----------------------------------------------------------------------------------
        assertThat(ok, is(true));
    }

    /**
     * Ensure that a listener can't call notify() for another listener protected by the same lock,
     * even if there's no listener currently registered
     */
    @Test(timeout = 300)
    public void sharedNotifyLockUnregistered() {
        // setup
        // -----------------------------------------------------------------------------------
        ReentrantLock groupLock = new ReentrantLock(true);
        final MpiEventPublisher<String> firstPublisher = new MpiEventPublisher<>(groupLock);
        final MpiEventPublisher<String> secondPublisher = new MpiEventPublisher<>(groupLock);
        MpiEventHandler<String> firstHandler = new MpiEventHandler<String>() {
            @Override
            public void handle(@NonNull String arg) {
                secondPublisher.notifyListener(arg);
            }
        };

        // execute
        // -----------------------------------------------------------------------------------
        boolean ok = firstPublisher.register(firstHandler);
        try {
            firstPublisher.notifyListener("Hello");
            Assert.fail("Expected ConcurrentModificationException");
        } catch (ConcurrentModificationException ignore) {
        }
        ok &= firstPublisher.deregister(firstHandler);

        // verify
        // -----------------------------------------------------------------------------------
        assertThat(ok, is(true));
    }

    /**
     * Ensure that an error in a listener releases the lock properly
     * (Though the error will usually propagate up to app -- but if they catch it and want to
     * continue on, we should support that)
     */
    @Test(timeout = 300)
    public void sharedNotifyLockThrows() {
        // setup
        // -----------------------------------------------------------------------------------
        ReentrantLock groupLock = new ReentrantLock(true);
        final MpiEventPublisher<String> firstPublisher = new MpiEventPublisher<>(groupLock);
        final MpiEventPublisher<String> secondPublisher = new MpiEventPublisher<>(groupLock);

        final List<String> arguments = new ArrayList<>(3);
        MpiEventHandler<String> firstHandler = new MpiEventHandler<String>() {
            @Override
            public void handle(@NonNull String arg) {
                throw new IllegalAccessError();
            }
        };
        final MpiEventHandler<String> secondHandler = new MpiEventHandler<String>() {
            @Override
            public void handle(@NonNull String arg) {
                arguments.add(arg);
            }
        };

        // execute
        // -----------------------------------------------------------------------------------
        boolean ok = firstPublisher.register(firstHandler);
        ok &= secondPublisher.register(secondHandler);
        //noinspection ErrorNotRethrown
        try {
            firstPublisher.notifyListener("disaster");
        } catch (IllegalAccessError ignore) {
        }
        secondPublisher.notifyListener("Disaster recovery!");

        ok &= firstPublisher.deregister(firstHandler);
        ok &= secondPublisher.deregister(secondHandler);

        // verify
        // -----------------------------------------------------------------------------------
        assertThat(ok, is(true));
        assertThat(join(" ", arguments), is(equalTo("Disaster recovery!")));
    }

    /**
     * Ensure that a listener can call notify() for another listener with a different lock
     */
    @Test(timeout = 300)
    public void nonSharedNotifyLock() {
        // setup
        // -----------------------------------------------------------------------------------
        ReentrantLock firstLock = new ReentrantLock(true);
        ReentrantLock secondLock = new ReentrantLock(true);
        final MpiEventPublisher<String> firstPublisher = new MpiEventPublisher<>(firstLock);
        final MpiEventPublisher<String> secondPublisher = new MpiEventPublisher<>(secondLock);
        MpiEventHandler<String> firstHandler = new MpiEventHandler<String>() {
            @Override
            public void handle(@NonNull String arg) {
                secondPublisher.notifyListener(arg);
            }
        };
        final List<String> arguments = new ArrayList<>(5);
        MpiEventHandler<String> secondHandler = new MpiEventHandler<String>() {
            @Override
            public void handle(@NonNull String arg) {
                arguments.add(arg);
            }
        };

        // execute
        // -----------------------------------------------------------------------------------
        boolean ok = firstPublisher.register(firstHandler);
        ok &= secondPublisher.register(secondHandler);
        ok &= firstPublisher.notifyListener("Hello");
        ok &= firstPublisher.notifyListener("World!");
        ok &= firstPublisher.deregister(firstHandler);
        ok &= secondPublisher.deregister(secondHandler);

        // verify
        // -----------------------------------------------------------------------------------
        assertThat(join(" ", arguments), is(equalTo("Hello World!")));
        assertThat(ok, is(true));
    }

    /**
     * There's no reason the same listener can't be used on multiple publishers in the same group
     */
    @Test(timeout = 300)
    public void sameListenerDifferentPublishersSameGroup() {
        // setup
        // -----------------------------------------------------------------------------------
        ReentrantLock groupLock = new ReentrantLock(true);
        final MpiEventPublisher<String> firstPublisher = new MpiEventPublisher<>(groupLock);
        final MpiEventPublisher<String> secondPublisher = new MpiEventPublisher<>(groupLock);
        final List<String> arguments = new ArrayList<>(5);
        MpiEventHandler<String> handler = new MpiEventHandler<String>() {
            @Override
            public void handle(@NonNull String arg) {
                arguments.add(arg);
            }
        };

        // execute
        // -----------------------------------------------------------------------------------
        boolean ok = firstPublisher.register(handler);
        ok &= secondPublisher.register(handler);

        ok &= firstPublisher.notifyListener("Hello");
        ok &= firstPublisher.notifyListener("World!");
        ok &= firstPublisher.deregister(handler);

        ok &= secondPublisher.notifyListener("How is it going?");
        ok &= secondPublisher.deregister(handler);

        // verify
        // -----------------------------------------------------------------------------------
        assertThat(join(" ", arguments), is(equalTo("Hello World! How is it going?")));
        assertThat(ok, is(true));
    }

    /**
     * Three threads spamming notify. Two to listener A and another to listener B, with
     * A and B being in the same group.
     * Two threads spamming register/deregistering, one for listener A and another for listener B.
     */
    @Test
    public void threadContentionTest() throws Exception {

        // setup
        // -----------------------------------------------------------------------------------
        // testLengthMillis is how long the test will last
        final long testLengthMillis = 1400L;

        class NotifySpammer implements Callable<Integer> {

            private final long mMaxMillis;
            private final Random mRandom;
            private final String mWho;
            private final MpiEventPublisher<String> mPublisher;

            private NotifySpammer(
                    String who,
                    MpiEventPublisher<String> publisher, long maxMillis, long randomSeed) {
                mWho = who;
                mPublisher = publisher;
                mMaxMillis = maxMillis;
                mRandom = new Random(randomSeed);
            }

            @Override
            public Integer call() throws Exception {
                long start = System.currentTimeMillis();
                int count = 0;

                // System.out.println("Started NotifySpammer " + mWho);

                long elapsedTime;
                do {
                    elapsedTime = System.currentTimeMillis() - start;

                    if (count == Integer.MAX_VALUE) {
                        System.out.println(
                                mWho + " max value hit after " + elapsedTime + " milliseconds");
                        return Integer.MAX_VALUE;
                    }

                    // System.out.println("Calling notifyListener " + mWho);
                    boolean ok = mPublisher.notifyListener(mWho);
                    if (ok) {
                        count += 1;
                    }
                    //noinspection ImplicitNumericConversion,BusyWait
                    TimeUnit.MILLISECONDS.sleep(intRange(mRandom, 5, 100));
                } while (elapsedTime < mMaxMillis);

                // System.out.println("NotifySpammer " + mWho + " exiting at elapsedTime:"
                //        + elapsedTime + " with count " + count);
                return count;
            }
        }

        class RegisterSpammer implements Callable<Void> {
            private final MpiEventHandler<String> mHandler;
            private final long mMaxMillis;
            private final Random mRandom;
            private final String mWho;
            private final MpiEventPublisher<String> mPublisher;

            private RegisterSpammer(
                    String who,
                    MpiEventPublisher<String> publisher,
                    MpiEventHandler<String> handler,
                    long maxMillis, long randomSeed) {
                mWho = who;
                mPublisher = publisher;
                mHandler = handler;
                mMaxMillis = maxMillis;
                mRandom = new Random(randomSeed);
            }

            @Override
            public Void call() throws Exception {
                long start = System.currentTimeMillis();

                // System.out.println("Started RegisterSpammer " + mWho);
                long elapsedTime;
                do {
                    elapsedTime = System.currentTimeMillis() - start;

                    // System.out.println("Calling register " + mWho);
                    if (!mPublisher.register(mHandler)) {
                        throw new AssertionError(mWho + " Failed to add handler");
                    }

                    //noinspection ImplicitNumericConversion,BusyWait
                    TimeUnit.MILLISECONDS.sleep((long) intRange(mRandom, 5, 300));

                    // System.out.println("Calling deregister " + mWho);
                    if (!mPublisher.deregister(mHandler)) {
                        throw new AssertionError(mWho + " Failed to remove handler");
                    }

                    TimeUnit.MILLISECONDS.sleep((long) intRange(mRandom, 5, 100));

                    // System.out.println(mWho + " elapsedTime:" + elapsedTime);
                    // System.out.println(mWho + " mMaxMillis:" + mMaxMillis);
                } while (elapsedTime < mMaxMillis);

                //System.out.println(
                //        "RegisterSpammer " + mWho + " exiting at elapsedTime:" + elapsedTime);

                //noinspection ReturnOfNull
                return null;
            }
        }

        long randomSeed = 123456L;

        final AtomicInteger atomicNotifyCounterA = new AtomicInteger(0);
        final AtomicInteger atomicNotifyCounterB = new AtomicInteger(0);
        ReentrantLock groupLock = new ReentrantLock(true);
        MpiEventPublisher<String> publisherA = new MpiEventPublisher<>(groupLock);
        MpiEventPublisher<String> publisherB = new MpiEventPublisher<>(groupLock);

        MpiEventHandler<String> handlerA = new MpiEventHandler<String>() {
            @Override
            public void handle(@NonNull String arg) {
                // System.out.println("handlerA called from " + arg);
                atomicNotifyCounterA.incrementAndGet();
            }
        };
        MpiEventHandler<String> handlerB = new MpiEventHandler<String>() {
            @Override
            public void handle(@NonNull String arg) {
                // System.out.println("handlerB called from " + arg);
                atomicNotifyCounterB.incrementAndGet();
            }
        };

        NotifySpammer notifySpammerA1 = new NotifySpammer(
                "A1", publisherA, testLengthMillis, randomSeed);
        NotifySpammer notifySpammerA2 = new NotifySpammer(
                "A2", publisherA, testLengthMillis, randomSeed + 1L);
        NotifySpammer notifySpammerB = new NotifySpammer(
                "B", publisherB, testLengthMillis, randomSeed + 2L);

        RegisterSpammer registerSpammerA = new RegisterSpammer(
                "A", publisherA, handlerA, testLengthMillis, randomSeed + 3L);
        RegisterSpammer registerSpammerB = new RegisterSpammer(
                "B", publisherB, handlerB, testLengthMillis, randomSeed + 4L);

        // execute
        // -----------------------------------------------------------------------------------
        ExecutorService executor = Executors.newFixedThreadPool(5);
        Future<Integer> notifierA1 = executor.submit(notifySpammerA1);
        Future<Integer> notifierA2 = executor.submit(notifySpammerA2);
        Future<Integer> notifierB = executor.submit(notifySpammerB);
        Future<Void> f3 = executor.submit(registerSpammerA);
        Future<Void> f4 = executor.submit(registerSpammerB);

        Integer spamCountA1 = notifierA1.get();
        Integer spamCountA2 = notifierA2.get();
        Integer spamCountB = notifierB.get();
        f3.get();
        f4.get();

        executor.shutdown();
        boolean termOk = executor.awaitTermination(testLengthMillis, TimeUnit.MILLISECONDS);

        // verify
        // -----------------------------------------------------------------------------------
        assertThat(termOk, is(true));
        assertThat(spamCountA1 + spamCountA2, is(equalTo(atomicNotifyCounterA.get())));
        assertThat(spamCountB, is(equalTo(atomicNotifyCounterB.get())));
    }

    /**
     * One thread spamming a bad self-notify and catching the error.
     * One thread spamming notify(A) which calls notify(B), and catches the error.
     */
    @Test
    public void threadedSelfNotify() throws Exception {

        // setup
        // -----------------------------------------------------------------------------------
        // testLengthMillis is how long the test will last
        final long testLengthMillis = 800L;

        class DodgyNotifySpammer implements Callable<Integer> {

            private final long mMaxMillis;
            private final Random mRandom;
            private final String mWho;
            private final MpiEventPublisher<String> mPublisher;

            private DodgyNotifySpammer(
                    String who,
                    MpiEventPublisher<String> publisher, long maxMillis, long randomSeed) {
                mWho = who;
                mPublisher = publisher;
                mMaxMillis = maxMillis;
                mRandom = new Random(randomSeed);
            }

            @Override
            public Integer call() throws Exception {
                long start = System.currentTimeMillis();
                int count = 0;

                // System.out.println("Started DodgyNotifySpammer " + mWho);

                long elapsedTime;
                do {
                    elapsedTime = System.currentTimeMillis() - start;

                    if (count == Integer.MAX_VALUE) {
                        System.out.println(
                                mWho + " max value hit after " + elapsedTime + " milliseconds");
                        return Integer.MAX_VALUE;
                    }

                    // System.out.println("Calling notifyListener " + mWho);
                    count += 1;
                    try {
                        mPublisher.notifyListener(mWho);
                        Assert.fail();
                    } catch (ConcurrentModificationException ignore) {
                    }
                    //noinspection ImplicitNumericConversion,BusyWait
                    TimeUnit.MILLISECONDS.sleep(intRange(mRandom, 5, 100));
                } while (elapsedTime < mMaxMillis);

                // System.out.println("DodgyNotifySpammer " + mWho + " exiting at elapsedTime:"
                //        + elapsedTime + " with count " + count);
                return count;
            }
        }

        long randomSeed = 23456789L;

        final AtomicInteger atomicNotifyCounterA = new AtomicInteger(0);
        final AtomicInteger atomicNotifyCounterB = new AtomicInteger(0);
        final ReentrantLock groupLock = new ReentrantLock(true);
        final MpiEventPublisher<String> publisherA = new MpiEventPublisher<>(groupLock);
        final MpiEventPublisher<String> publisherB = new MpiEventPublisher<>(groupLock);

        MpiEventHandler<String> handlerA = new MpiEventHandler<String>() {
            @Override
            public void handle(@NonNull String arg) {
                // System.out.println("handlerA called from " + arg);
                atomicNotifyCounterA.incrementAndGet();
                publisherA.notifyListener(arg + "+handlerA!");
            }
        };
        MpiEventHandler<String> handlerB = new MpiEventHandler<String>() {
            @Override
            public void handle(@NonNull String arg) {
                // System.out.println("handlerB called from " + arg);
                atomicNotifyCounterB.incrementAndGet();
                publisherB.notifyListener(arg + "+handlerB");
            }
        };

        publisherA.register(handlerA);
        publisherB.register(handlerB);

        DodgyNotifySpammer notifySpammerSelf = new DodgyNotifySpammer(
                "self-notify", publisherA, testLengthMillis, randomSeed);
        DodgyNotifySpammer notifySpammerCross = new DodgyNotifySpammer(
                "cross-notify", publisherB, testLengthMillis, randomSeed + 1L);

        // execute
        // -----------------------------------------------------------------------------------
        ExecutorService executor = Executors.newFixedThreadPool(5);
        Future<Integer> notifierA1 = executor.submit(notifySpammerSelf);
        Future<Integer> notifierA2 = executor.submit(notifySpammerCross);

        Integer spamCountSelf = notifierA1.get();
        Integer spamCountCross = notifierA2.get();

        executor.shutdown();
        boolean termOk = executor.awaitTermination(testLengthMillis, TimeUnit.MILLISECONDS);

        // verify
        // -----------------------------------------------------------------------------------
        assertThat(termOk, is(true));
        assertThat(spamCountSelf, is(equalTo(spamCountSelf)));
        assertThat(spamCountCross, is(equalTo(spamCountCross)));
    }

    private static int intRange(Random random, int min, int max) {
        if (max < min) throw new AssertionError("max < min");
        int range = (max - min) + 1; // convert from inclusive to exclusive
        return random.nextInt(range) + min;
    }

    /** Java 7 is a joke and doesn't even have join. Can't use Android TextUtils in test */
    private static String join(String delimiter, List<String> strings) {
        if (strings.size() == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(strings.get(0));

        for (String s : strings.subList(1, strings.size())) {
            sb.append(delimiter);
            sb.append(s);
        }
        return sb.toString();
    }

}


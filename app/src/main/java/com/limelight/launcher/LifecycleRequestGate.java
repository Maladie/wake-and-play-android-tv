package com.limelight.launcher;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Invalidates asynchronous UI work whenever its owning lifecycle becomes inactive.
 * A token from an earlier activation can never become current again.
 */
final class LifecycleRequestGate {
    private final AtomicInteger generation = new AtomicInteger();
    private volatile boolean active;

    int activate() {
        int token = generation.incrementAndGet();
        active = true;
        return token;
    }

    void deactivate() {
        active = false;
        generation.incrementAndGet();
    }

    int currentToken() {
        return generation.get();
    }

    boolean isCurrent(int token) {
        return active && token == generation.get();
    }
}

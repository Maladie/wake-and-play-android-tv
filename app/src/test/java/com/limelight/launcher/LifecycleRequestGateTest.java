package com.limelight.launcher;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LifecycleRequestGateTest {
    @Test
    public void activeTokenIsCurrentUntilDeactivation() {
        LifecycleRequestGate gate = new LifecycleRequestGate();

        int token = gate.activate();

        assertTrue(gate.isCurrent(token));
        gate.deactivate();
        assertFalse(gate.isCurrent(token));
    }

    @Test
    public void reactivationDoesNotRevivePreviousToken() {
        LifecycleRequestGate gate = new LifecycleRequestGate();
        int previousToken = gate.activate();
        gate.deactivate();

        int currentToken = gate.activate();

        assertFalse(gate.isCurrent(previousToken));
        assertTrue(gate.isCurrent(currentToken));
    }

    @Test
    public void tokenCapturedWhileInactiveCannotBecomeCurrent() {
        LifecycleRequestGate gate = new LifecycleRequestGate();
        int inactiveToken = gate.currentToken();

        int activeToken = gate.activate();

        assertFalse(gate.isCurrent(inactiveToken));
        assertTrue(gate.isCurrent(activeToken));
    }
}

package org.poolen.web.events;

import org.springframework.context.ApplicationEvent;

/**
 * A simple Spring application event to signal that the
 * Google authentication status has changed (e.g., user logged in or out).
 *
 * This is a "signal" event; it doesn't need to carry data,
 * it just tells listeners to re-check the status.
 */
public class AuthStatusChangedEvent extends ApplicationEvent {

    /**
     * Create a new AuthStatusChangedEvent.
     * @param source The component that published the event (e.g., GoogleAuthManager).
     */
    public AuthStatusChangedEvent(Object source) {
        super(source);
    }
}

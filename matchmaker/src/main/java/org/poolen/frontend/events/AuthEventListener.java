package org.poolen.frontend.events;

import javafx.application.Platform;
import org.poolen.frontend.util.services.ComponentFactoryService;
import org.poolen.web.events.AuthStatusChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * A dedicated, single-responsibility Spring Component to act as a
 * bridge between Spring's ApplicationEvent system and the JavaFX UI.
 *
 * Its only job is to listen for the AuthStatusChangedEvent and
 * tell the JavaFX UI (via the ComponentFactoryService) to update.
 *
 */
@Component
public class AuthEventListener {

    private static final Logger logger = LoggerFactory.getLogger(AuthEventListener.class);

    // We just need the factory so we can get the stage
    private final ComponentFactoryService componentFactory;

    @Autowired
    public AuthEventListener(ComponentFactoryService componentFactory) {
        this.componentFactory = componentFactory;
        logger.info("AuthEventListener initialised and ready to listen!");
    }

    /**
     * Listens for our AuthStatusChangedEvent.
     * @param event The auth status changed event.
     */
    @EventListener
    public void handleAuthChange(AuthStatusChangedEvent event) {
        logger.info("AuthStatusChangedEvent received! Relaying to ManagementStage...");

        // We still need to use Platform.runLater
        // to keep our threads safe!
        Platform.runLater(() -> {
            // We just ask the factory for the stage and tell it to refresh!
            componentFactory.getManagementStage().refreshAuthStatus();
        });
    }
}

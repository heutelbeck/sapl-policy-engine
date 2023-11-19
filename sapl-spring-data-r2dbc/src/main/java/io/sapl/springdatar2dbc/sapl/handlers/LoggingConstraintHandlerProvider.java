package io.sapl.springdatar2dbc.sapl.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.logging.Logger;

/**
 * This class represents the logging constraint handler provider.
 */
public class LoggingConstraintHandlerProvider {

    private final Logger logger = Logger.getLogger(LoggingConstraintHandlerProvider.class.getName());

    /**
     * Checks if an obligation of a {@link io.sapl.api.pdp.Decision} is responsible
     * and can be applied.
     *
     * @param constraint is an obligation of a {@link io.sapl.api.pdp.Decision}
     * @return true if the obligation can be applied.
     */
    public boolean isResponsible(JsonNode constraint) {
        if (constraint == null) {
            return false;
        }
        return constraint.has("message") && constraint.has("id") && "log".equals(constraint.get("id").asText());
    }

    /**
     * Get the handler to be able to use it.
     *
     * @param constraint is an obligation of a {@link io.sapl.api.pdp.Decision}
     * @return a {@link Runnable}
     */
    public Runnable getHandler(JsonNode constraint) {
        return () -> {
            if (constraint != null && constraint.has("message")) {
                var message = constraint.findValue("message").asText();
                this.logger.info(message);
            }
        };
    }

}

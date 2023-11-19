package io.sapl.springdatamongoreactive.sapl.handlers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(OutputCaptureExtension.class)
class LoggingConstraintHandlerProviderTest {

    LoggingConstraintHandlerProvider loggingConstraintHandlerProvider = new LoggingConstraintHandlerProvider();

    final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void when_constraintIsResponsible_then_returnTrue() throws JsonProcessingException {
        // GIVEN
        var constraint = objectMapper
                .readTree("{\"id\": \"log\",\"message\": \"You are using SAPL for protection of database.\"}");

        // WHEN
        var actual = loggingConstraintHandlerProvider.isResponsible(constraint);

        // THEN
        Assertions.assertTrue(actual);
    }

    @Test
    void when_constraintIsNull_then_returnFalse() {
        // GIVEN

        // WHEN
        var actual = loggingConstraintHandlerProvider.isResponsible(null);

        // THEN
        Assertions.assertFalse(actual);
    }

    @Test
    void when_constraintIsResponsible_then_returnFalse() throws JsonProcessingException {
        // GIVEN
        var constraintNotValid = objectMapper
                .readTree("{\"idTest\": \"log\",\"message\": \"You are using SAPL for protection of database.\"}");

        // WHEN
        var actual = loggingConstraintHandlerProvider.isResponsible(constraintNotValid);

        // THEN
        Assertions.assertFalse(actual);
    }

    @Test
    void when_constraintIsResponsible_then_getHandler(CapturedOutput capturedOutput) throws JsonProcessingException {
        // GIVEN
        var constraint = objectMapper
                .readTree("{\"id\": \"log\",\"message\": \"You are using SAPL for protection of database.\"}");

        // WHEN
        var actual = loggingConstraintHandlerProvider.getHandler(constraint);
        actual.run();

        // THEN
        assertTrue(capturedOutput.getOut().contains("You are using SAPL for protection of database."));
    }
}

package io.sapl.server.ce.service.pdpconfiguration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ConfigurationExceptionTests {
    @Test
    public void nullResistance() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            new DuplicatedVariableNameException(null);
        });

        Assertions.assertThrows(NullPointerException.class, () -> {
            new InvalidJsonException(null);
        });
        Assertions.assertThrows(NullPointerException.class, () -> {
            new InvalidJsonException(null, new Exception());
        });

        Assertions.assertThrows(NullPointerException.class, () -> {
            new InvalidVariableNameException(null);
        });
    }
}

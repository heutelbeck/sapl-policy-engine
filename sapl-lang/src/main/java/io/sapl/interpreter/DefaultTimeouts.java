package io.sapl.interpreter;

import java.time.Duration;

import lombok.experimental.UtilityClass;

@UtilityClass
public class DefaultTimeouts {
    public static final Duration DEFAULT_GRACE_PERIOD      = Duration.ofSeconds(1L);
    public static final Duration DEFAULT_ATTRIBUTE_TIMEOUT = Duration.ofSeconds(1L);
}

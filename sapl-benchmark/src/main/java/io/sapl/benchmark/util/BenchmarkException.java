package io.sapl.benchmark.util;

import io.sapl.api.SaplVersion;
import lombok.experimental.StandardException;

import java.io.Serial;

@StandardException
public class BenchmarkException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;
}

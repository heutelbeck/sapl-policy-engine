package io.sapl.broker.impl;

import static io.sapl.broker.impl.NameValidator.requireValidName;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import io.sapl.api.interpreter.Val;
import lombok.NonNull;

public record PolicyInformationPointInvocation(@NonNull String fullyQualifiedAttributeName, Val entity,
        @NonNull List<Val> arguments, @NonNull Map<String, Val> variables, @NonNull Duration initialTimeOut,
        @NonNull Duration pollIntervall, @NonNull Duration backoff, long retries) {

    public PolicyInformationPointInvocation {
        requireValidName(fullyQualifiedAttributeName);
    }

}

package io.sapl.api.broker;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import io.sapl.api.interpreter.Val;
import lombok.NonNull;
import reactor.core.publisher.Flux;

public interface AttributeStreamBroker {

    Flux<Val> attributeStream(@NonNull String pdpConfigurationId, @NonNull String attributeName, @NonNull Val entity,
            @NonNull List<Val> arguments, @NonNull Map<String, Val> variables, @NonNull Duration initialTimeOut,
            @NonNull Duration pollIntervall, @NonNull Duration backoff, long retries, boolean fresh);

    Flux<Val> environmentAttributeStream(@NonNull String pdpConfigurationId, @NonNull String environemntAttributeName,
            @NonNull List<Val> arguments, @NonNull Map<String, Val> variables, @NonNull Duration initialTimeOut,
            @NonNull Duration pollIntervall, @NonNull Duration backoff, long retries, boolean fresh);

}

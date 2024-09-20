package io.sapl.broker.impl.bunker;

import java.util.List;
import java.util.Map;

import io.sapl.api.interpreter.Val;
import reactor.core.publisher.Flux;

public interface EnvironmentAttribute extends Attribute {
    Flux<Val> evaluateEnvironmentAttribute(List<Val> arguments, Map<String, Val> variables);
}

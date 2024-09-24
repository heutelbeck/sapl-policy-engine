package io.sapl.broker.impl;

import io.sapl.api.interpreter.Val;
import reactor.core.publisher.Flux;

@FunctionalInterface
public interface PolicyInformationPoint {
    Flux<Val> invoce(PolicyInformationPointInvocation invocation);
}
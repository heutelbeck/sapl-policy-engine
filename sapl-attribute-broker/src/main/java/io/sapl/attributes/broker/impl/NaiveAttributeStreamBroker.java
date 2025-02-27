package io.sapl.attributes.broker.impl;

import java.util.Collection;
import java.util.List;

import io.sapl.api.interpreter.Val;
import io.sapl.attributes.broker.api.AttributeFinderInvocation;
import io.sapl.attributes.broker.api.AttributeStreamBroker;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
public class NaiveAttributeStreamBroker implements AttributeStreamBroker {
    @Override
    public Flux<Val> attributeStream(AttributeFinderInvocation invocation) {
        // TODO
        return Flux.just(Val.of("DUMMY DATA"));
    }

    @Override
    public Collection<String> providedFunctionsOfLibrary(String library) {
        return List.of("test.test");
    }

    @Override
    public boolean isProvidedFunction(String fullyQualifiedFunctionName) {
        return fullyQualifiedFunctionName.equals("test.test");
    }
}

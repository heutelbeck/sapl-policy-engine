package io.sapl.vaadin.constraint;

import java.util.Iterator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import com.fasterxml.jackson.databind.JsonNode;
import com.vaadin.flow.component.UI;

import reactor.core.publisher.Mono;

public interface VaadinFunctionConstraintHandlerProvider {
    boolean isResponsible(JsonNode constraint);
    Function<UI, Mono<Boolean>> getHandler(JsonNode constraint);

    static VaadinFunctionConstraintHandlerProvider of(Predicate<JsonNode> isResponsible, Function<UI, Mono<Boolean>> handler) {
        return new VaadinFunctionConstraintHandlerProvider() {

            @Override
            public boolean isResponsible(JsonNode constraint) {
                return isResponsible.test(constraint);
            }

            @Override
            public Function<UI, Mono<Boolean>> getHandler(JsonNode constraint) {
                return handler;
            }
        };
    }

    static VaadinFunctionConstraintHandlerProvider of(JsonNode constraintFilter, Consumer<JsonNode> handler){
        return new VaadinFunctionConstraintHandlerProvider() {

            @Override
            public boolean isResponsible(JsonNode constraint) {
                boolean isResponsible = true;
                for (Iterator<String> it = constraintFilter.fieldNames(); it.hasNext(); ) {
                    String filterField = it.next();
                    if (!constraint.has(filterField) || !constraint.get(filterField).equals(constraintFilter.get(filterField))) {
                        isResponsible = false;
                    }
                }
                return isResponsible;
            }

            @Override
            public Function<UI, Mono<Boolean>> getHandler(JsonNode constraint) {
                return (ui)-> {
                    handler.accept(constraint);
                    return Mono.just(Boolean.TRUE);
                };
            }
        };
    }

    static VaadinFunctionConstraintHandlerProvider of(JsonNode constraintFilter, Function<JsonNode, Mono<Boolean>> handler){
        return new VaadinFunctionConstraintHandlerProvider() {
            @Override
            public boolean isResponsible(JsonNode constraint) {
                boolean isResponsible = true;
                for (Iterator<String> it = constraintFilter.fieldNames(); it.hasNext(); ) {
                    String filterField = it.next();
                    if (!constraint.has(filterField) || !constraint.get(filterField).equals(constraintFilter.get(filterField))) {
                        isResponsible = false;
                    }
                }
                return isResponsible;
            }

            @Override
            public Function<UI, Mono<Boolean>> getHandler(JsonNode constraint) {
                return (ui)-> handler.apply(constraint);
            }
        };
    }
}

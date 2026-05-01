/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.spring.pep.constraints;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.core.ResolvableType;

import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.spring.pep.constraints.Signal.OutputSignal;

/**
 * Translates one constraint into the constraint handlers that enforce it.
 * <p>
 * A provider returns an empty list when it does not recognise the
 * constraint. Otherwise it returns one or more
 * {@link ScopedConstraintHandler} entries. Each entry pairs a handler
 * with the {@link SignalType} it attaches to and a priority. The planner
 * schedules every returned handler against its signal independently, so
 * a single obligation can drive several handlers across different
 * lifecycle points (for example, audit on the decision and a header
 * stamp on the response).
 * <p>
 * Implementations use {@code supportedSignals} to discover which
 * {@link SignalType} instances the deployed PEP actually fires (such as
 * the {@link Signal.OutputSignal} type bound to a concrete value type)
 * and only return handlers whose signal type is in that set.
 */
public interface ConstraintHandlerProvider {

    /**
     * Returns the handlers that enforce {@code constraint}.
     *
     * @param constraint the constraint value from the authorization decision.
     * @param supportedSignals signal types the deployed PEP advertises.
     * @return an empty list when the provider does not handle this
     * constraint, or a non-empty list of scoped handlers to schedule.
     */
    List<ScopedConstraintHandler> getConstraintHandlers(Value constraint, Set<SignalType> supportedSignals);

    /**
     * True iff {@code constraint} is an {@link ObjectValue} whose
     * {@code "type"} field is a {@link TextValue} equal to
     * {@code expectedType}. Convenience for the dispatch-on-type guard
     * that nearly every provider opens with.
     *
     * @param constraint the constraint value to inspect.
     * @param expectedType the expected value of the {@code "type"} field.
     * @return true when the constraint is a typed object matching
     * {@code expectedType}.
     */
    static boolean constraintIsOfType(Value constraint, String expectedType) {
        return constraint instanceof ObjectValue obj && obj.get("type") instanceof TextValue(String type)
                && expectedType.equals(type);
    }

    /**
     * Returns the string value of a named field from a typed-object
     * constraint. Empty when {@code constraint} is not an
     * {@link ObjectValue}, when the field is absent, or when the field
     * is not a {@link TextValue}.
     *
     * @param constraint the constraint value to inspect.
     * @param fieldName the field to read.
     * @return the field's textual value if present, empty otherwise.
     */
    static Optional<String> stringField(Value constraint, String fieldName) {
        if (!(constraint instanceof ObjectValue obj)) {
            return Optional.empty();
        }
        if (!(obj.get(fieldName) instanceof TextValue(String value))) {
            return Optional.empty();
        }
        return Optional.of(value);
    }

    /**
     * Returns a map keyed by {@code fieldNames} containing the string
     * value of each named field. Empty when any required field is
     * missing or not a {@link TextValue}. Use when a provider needs
     * several mandatory string fields and would otherwise repeat
     * {@link #stringField} guards. The returned map preserves
     * insertion order.
     *
     * @param constraint the constraint value to inspect.
     * @param fieldNames the field names that must all be present and
     * textual.
     * @return all fields as a name-to-value map when every field is
     * present and textual; empty otherwise.
     */
    static Optional<Map<String, String>> requiredStringFields(Value constraint, String... fieldNames) {
        var map = new LinkedHashMap<String, String>();
        for (var name : fieldNames) {
            var fieldOpt = stringField(constraint, name);
            if (fieldOpt.isEmpty()) {
                return Optional.empty();
            }
            map.put(name, fieldOpt.get());
        }
        return Optional.of(map);
    }

    /**
     * Combined check: the constraint is of {@code constraintType} and
     * {@code targetSignal} (a singleton SIGNAL_TYPE constant such as
     * {@code DecisionSignal.SIGNAL_TYPE}) is in
     * {@code supportedSignals}. Returns {@code targetSignal} so the
     * provider can bind a {@link ScopedConstraintHandler} without
     * repeating the constant. Use for any signal type other than
     * {@link OutputSignal}, which is per-method-typed and needs the
     * dedicated {@link #constraintTypeAndOutputSignal} helper.
     */
    static Optional<SignalType> constraintTypeAndSignal(Value constraint, String constraintType,
            Set<SignalType> supportedSignals, SignalType targetSignal) {
        if (!constraintIsOfType(constraint, constraintType)) {
            return Optional.empty();
        }
        if (!supportedSignals.contains(targetSignal)) {
            return Optional.empty();
        }
        return Optional.of(targetSignal);
    }

    /**
     * Combined check: the constraint is of {@code constraintType} and
     * {@code supportedSignals} contains an {@link OutputSignal} whose
     * value type is {@code outerClass} parameterised by
     * {@code generics}. The match is structural by resolved class
     * recursively, so a value type constructed by the deployed PEP via
     * {@link ResolvableType#forMethodReturnType} matches a helper-side
     * description constructed via
     * {@link ResolvableType#forClassWithGenerics} for the same shape.
     * Returns the matched {@link OutputSignal} signal type so the
     * provider can bind a typed Mapper to it.
     * <p>
     * Examples: {@code constraintTypeAndOutputSignal(constraint,
     * "filterDocs", supportedSignals, Flux.class, Document.class)} for
     * a method returning {@code Flux<Document>};
     * {@code constraintTypeAndOutputSignal(constraint, "wrap",
     * supportedSignals, String.class)} for a method returning
     * {@code Mono<String>} (Mono unwraps to the element type at the
     * Mono PEP boundary, see
     * {@link io.sapl.spring.pep.method.reactive.PreEnforcePolicyEnforcementPoint}).
     */
    static Optional<SignalType> constraintTypeAndOutputSignal(Value constraint, String constraintType,
            Set<SignalType> supportedSignals, Class<?> outerClass, Class<?>... generics) {
        if (!constraintIsOfType(constraint, constraintType)) {
            return Optional.empty();
        }
        var expected = generics.length == 0 ? ResolvableType.forClass(outerClass)
                : ResolvableType.forClassWithGenerics(outerClass, generics);
        for (var s : supportedSignals) {
            if (s instanceof SignalType.ValueSignalType<?> v && OutputSignal.class.equals(v.type())
                    && resolvableTypesMatch(v.valueType(), expected)) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }

    /**
     * Combined check for providers whose handler does not depend on the
     * value type of the {@link OutputSignal} (a logging side-effect
     * that observes the value as Object, for example). Use sparingly:
     * most providers should pin the type via
     * {@link #constraintTypeAndOutputSignal} so the bound Mapper is
     * type-correct.
     */
    static Optional<SignalType> constraintTypeAndAnyOutputSignal(Value constraint, String constraintType,
            Set<SignalType> supportedSignals) {
        if (!constraintIsOfType(constraint, constraintType)) {
            return Optional.empty();
        }
        return SignalType.findIn(supportedSignals, OutputSignal.class).map(s -> (SignalType) s);
    }

    /**
     * Recursive structural comparison of two {@link ResolvableType}
     * instances by resolved class and generics. Used by
     * {@link #constraintTypeAndOutputSignal} so a constructed
     * {@code forClassWithGenerics} matches a {@code forMethodReturnType}
     * for the same shape, avoiding {@link ResolvableType#equals}'s
     * underlying-type comparison which differs between synthetic and
     * reflection-derived parameterised types.
     */
    private static boolean resolvableTypesMatch(ResolvableType actual, ResolvableType expected) {
        if (actual.toClass() != expected.toClass()) {
            return false;
        }
        var actualGenerics   = actual.getGenerics();
        var expectedGenerics = expected.getGenerics();
        if (actualGenerics.length != expectedGenerics.length) {
            return false;
        }
        for (var i = 0; i < actualGenerics.length; i++) {
            if (!resolvableTypesMatch(actualGenerics[i], expectedGenerics[i])) {
                return false;
            }
        }
        return true;
    }
}

/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.test.dsl.interpreter;

import io.sapl.api.attributes.PolicyInformationPoint;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.test.SaplTestException;
import io.sapl.test.SaplTestFixture;
import io.sapl.test.grammar.sapltest.*;
import lombok.RequiredArgsConstructor;

import java.lang.Object;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RequiredArgsConstructor
class DefaultTestFixtureConstructor {

    private final DocumentInterpreter documentInterpreter;

    SaplTestFixture constructTestFixture(final Document document, final PdpVariables ignoredPdpVariables,
            final PdpCombiningAlgorithm ignoredPdpCombiningAlgorithm, final List<GivenStep> givenSteps,
            final Map<ImportType, Map<String, Object>> fixtureRegistrations) {

        var saplTestFixture = documentInterpreter.getFixtureFromDocument(document);

        if (saplTestFixture == null) {
            throw new SaplTestException("TestFixture is null.");
        }

        if (givenSteps != null) {

            if (givenSteps.stream().anyMatch(Objects::isNull)) {
                throw new SaplTestException("GivenStep is null.");
            }

            final var imports = givenSteps.stream().filter(Import.class::isInstance).map(Import.class::cast).toList();

            handleFixtureRegistrations(saplTestFixture, imports, fixtureRegistrations);
        }

        return saplTestFixture;
    }

    private void handleFixtureRegistrations(final SaplTestFixture fixture, final List<Import> imports,
            final Map<ImportType, Map<String, Object>> fixtureRegistrations) {
        for (var specificImport : imports) {

            final var registrationType = specificImport.getType();
            final var identifier       = specificImport.getIdentifier();

            if (registrationType == null || identifier == null) {
                throw new SaplTestException("Invalid Import");
            }

            final var registration = getRegistrationForType(fixtureRegistrations, registrationType, identifier);

            switch (specificImport.getType()) {
            case PIP                     -> handlePip(fixture, registration, identifier);
            case STATIC_PIP              -> handleStaticPip(fixture, registration, identifier);
            case FUNCTION_LIBRARY        -> handleFunctionLibrary(fixture, registration, identifier);
            case STATIC_FUNCTION_LIBRARY -> handleStaticFunctionLibrary(fixture, registration, identifier);
            }
        }
    }

    private void handlePip(final SaplTestFixture fixture, final Object registration, final String identifier) {
        checkForValidRegistration(registration, PolicyInformationPoint.class, identifier);

        fixture.registerPIP(registration);
    }

    private void handleStaticPip(final SaplTestFixture fixture, final Object registration, final String identifier) {
        final var pipClass = checkForClassTypeRegistration(registration, PolicyInformationPoint.class, identifier);

        fixture.registerPIP(pipClass);
    }

    private void handleFunctionLibrary(final SaplTestFixture fixture, final Object registration,
            final String identifier) {
        checkForValidRegistration(registration, FunctionLibrary.class, identifier);

        fixture.registerFunctionLibrary(registration);
    }

    private void handleStaticFunctionLibrary(final SaplTestFixture fixture, final Object registration,
            final String identifier) {
        final var functionLibraryClass = checkForClassTypeRegistration(registration, FunctionLibrary.class, identifier);

        fixture.registerFunctionLibrary(functionLibraryClass);
    }

    private void checkForValidRegistration(final Object registration, final Class<? extends Annotation> annotationClass,
            final String identifier) {
        final var annotation = registration.getClass().getAnnotation(annotationClass);

        if (annotation == null) {
            throw new SaplTestException("registration with name \"%s\" is missing the \"%s\" annotation"
                    .formatted(identifier, annotationClass.getSimpleName()));
        }
    }

    private Class<?> checkForClassTypeRegistration(final Object registration,
            final Class<? extends Annotation> annotationClass, final String identifier) {
        final var annotationName = annotationClass.getSimpleName();

        if (!(registration instanceof Class<?> staticClass)) {
            throw new SaplTestException("Static \"%s\" registration with name \"%s\" is not a class type"
                    .formatted(annotationClass.getSimpleName(), identifier));
        }

        final var annotation = staticClass.getAnnotation(annotationClass);

        if (annotation == null) {
            throw new SaplTestException("Class is missing the \"%s\" annotation".formatted(annotationName));
        }

        return staticClass;
    }

    private Object getRegistrationForType(final Map<ImportType, Map<String, Object>> registrations,
            final ImportType importType, final String identifier) {

        if (registrations == null || registrations.isEmpty()) {
            throw new SaplTestException("No FixtureRegistrations present, please check your setup");
        }

        final var registrationsForType = registrations.get(importType);

        if (registrationsForType == null || registrationsForType.isEmpty()) {
            throw new SaplTestException("No registrations for type \"%s\" found".formatted(importType));
        }

        final var registration = registrationsForType.get(identifier);

        if (registration == null) {
            throw new SaplTestException(
                    "No \"%s\" registration for name \"%s\" found".formatted(importType, identifier));
        }

        return registration;
    }
}

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
package io.sapl.functions;

import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.functions.FunctionInvocation;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.functions.FunctionSpecification;
import io.sapl.api.model.Value;
import io.sapl.api.shared.Match;
import lombok.Getter;
import lombok.val;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Default implementation of function broker managing registration and
 * invocation of SAPL functions from function
 * libraries.
 * <p>
 * Threading Model:
 * <ul>
 * <li>Library loading is typically performed at application startup before
 * policy evaluation begins</li>
 * <li>Function evaluation is lock-free and supports thousands of concurrent
 * calls for optimal performance in reactive
 * policy evaluation</li>
 * <li>Dynamic runtime library loading is supported but rare. For atomic
 * multi-library updates, create a new broker
 * instance and swap references</li>
 * </ul>
 * <p>
 * Concurrency: Per-function registration is atomic via
 * ConcurrentHashMap.compute(). Concurrent dynamic loading of
 * different functions is safe. Loading the same function concurrently will
 * result in collision detection on one thread.
 */
public class DefaultFunctionBroker implements FunctionBroker {

    private static final String CLASS_HAS_NO_FUNCTION_LIBRARY_ANNOTATION_ERROR = "Provided class has no @FunctionLibrary annotation.";
    private static final String FUNCTION_COLLISION_ERROR                       = "Function collision error for '%s'. A function with the same signature already exists.";
    private static final String NO_MATCHING_FUNCTION_FOUND_ERROR               = "No matching function found for %s.";
    private static final String LIBRARY_CLASS_NULL_ERROR                       = "Library class must not be null.";
    private static final String LIBRARY_INSTANCE_NULL_ERROR                    = "Library instance must not be null.";
    private static final String INVOCATION_NULL_ERROR                          = "Function invocation must not be null.";

    private final Map<String, List<FunctionSpecification>> functionIndex = new ConcurrentHashMap<>();

    @Getter
    private final List<Class<?>> registeredLibraries = new CopyOnWriteArrayList<>();

    /**
     * Loads a static function library from the provided class.
     * <p>
     * The class must be annotated with {@link FunctionLibrary}. All methods
     * annotated as functions will be registered.
     * <p>
     * Thread-safety: Safe to call during initialization. Concurrent loading of
     * different libraries is supported.
     * Collision detection ensures duplicate function signatures are rejected.
     *
     * @param libraryClass
     * the class containing static function methods
     *
     * @throws IllegalStateException
     * if the class lacks @FunctionLibrary annotation
     * @throws IllegalArgumentException
     * if libraryClass is null or function collision detected
     */
    public void loadStaticFunctionLibrary(Class<?> libraryClass) {
        if (libraryClass == null) {
            throw new IllegalArgumentException(LIBRARY_CLASS_NULL_ERROR);
        }
        loadLibrary(null, libraryClass);
        registeredLibraries.add(libraryClass);
    }

    /**
     * Loads a function library from the provided instance.
     * <p>
     * The instance's class must be annotated with {@link FunctionLibrary}. All
     * methods annotated as functions will be
     * registered.
     * <p>
     * Thread-safety: Safe to call during initialization. Concurrent loading of
     * different libraries is supported.
     * Collision detection ensures duplicate function signatures are rejected.
     *
     * @param libraryInstance
     * the object instance containing function methods
     *
     * @throws IllegalStateException
     * if the class lacks @FunctionLibrary annotation
     * @throws IllegalArgumentException
     * if libraryInstance is null or function collision detected
     */
    public void loadInstantiatedFunctionLibrary(Object libraryInstance) {
        if (libraryInstance == null) {
            throw new IllegalArgumentException(LIBRARY_INSTANCE_NULL_ERROR);
        }
        loadLibrary(libraryInstance, libraryInstance.getClass());
        registeredLibraries.add(libraryInstance.getClass());
    }

    private void loadLibrary(Object library, Class<?> libraryType) {
        val libAnnotation = libraryType.getAnnotation(FunctionLibrary.class);

        if (libAnnotation == null) {
            throw new IllegalStateException(CLASS_HAS_NO_FUNCTION_LIBRARY_ANNOTATION_ERROR);
        }

        var libName = libAnnotation.name();
        if (libName.isBlank()) {
            libName = libraryType.getSimpleName();
        }

        for (Method method : libraryType.getDeclaredMethods()) {
            val spec = MethodSignatureProcessor.functionSpecification(library, libName, method);
            if (spec != null) {
                loadFunction(spec);
            }
        }
    }

    private void loadFunction(FunctionSpecification functionSpecification) {
        functionIndex.compute(functionSpecification.functionName(), (functionName, functions) -> {
            var functionList = functions != null ? functions : new ArrayList<FunctionSpecification>();

            validateNoCollision(functionList, functionSpecification);

            functionList.add(functionSpecification);
            return functionList;
        });
    }

    private void validateNoCollision(List<FunctionSpecification> existingFunctions, FunctionSpecification newFunction) {
        for (val spec : existingFunctions) {
            if (spec.collidesWith(newFunction)) {
                throw new IllegalArgumentException(FUNCTION_COLLISION_ERROR.formatted(newFunction.functionName()));
            }
        }
    }

    /**
     * Evaluates a function invocation by finding the best matching function
     * specification and applying it.
     * <p>
     * The matching algorithm compares the invocation against all registered
     * functions with the same name, selecting the
     * best match based on parameter type compatibility.
     * <p>
     * Performance: This method is lock-free and optimized for high-throughput
     * concurrent access. It is called on the
     * hot path during policy evaluation.
     *
     * @param invocation
     * the function invocation containing name and parameters
     *
     * @return the result of the function evaluation, or an error Value if no match
     * found
     *
     * @throws IllegalArgumentException
     * if invocation is null
     */
    @Override
    public Value evaluateFunction(FunctionInvocation invocation) {
        if (invocation == null) {
            throw new IllegalArgumentException(INVOCATION_NULL_ERROR);
        }

        val specs = functionIndex.get(invocation.functionName());

        if (specs != null) {
            FunctionSpecification bestMatch = null;
            var                   match     = Match.NO_MATCH;

            for (val spec : specs) {
                val newMatch = invocation.matches(spec);
                if (newMatch.isBetterThan(match)) {
                    match     = newMatch;
                    bestMatch = spec;
                }
            }

            if (bestMatch != null) {
                return bestMatch.function().apply(invocation);
            }
        }

        return Value.error(NO_MATCHING_FUNCTION_FOUND_ERROR, invocation);
    }

}

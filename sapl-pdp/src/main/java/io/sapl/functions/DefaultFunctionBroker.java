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

import static io.sapl.api.shared.NameValidator.requireValidName;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.functions.BoundFunction;
import io.sapl.api.functions.FunctionInvocation;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.functions.FunctionSpecification;
import io.sapl.api.functions.NoMatchingFunctionException;
import io.sapl.api.model.Value;
import io.sapl.api.shared.Match;

import lombok.Getter;
import lombok.val;

/**
 * Default implementation of function broker managing registration and
 * invocation of SAPL functions from function
 * libraries.
 * <p>
 * Threading Model:
 * <ul>
 * <li>Library loading is typically performed at application startup before
 * policy evaluation begins</li>
 * <li>Library code executes outside the result cache, so independent requests
 * can evaluate concurrently</li>
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

    private static final String ERROR_AMBIGUOUS_FUNCTION_MATCH       = "Ambiguous function match for %s. Multiple registered functions match this invocation equally well.";
    private static final String ERROR_ARGUMENT_COUNT_NEGATIVE        = "Function argument count must not be negative.";
    private static final String ERROR_FUNCTION_BINDING_DESCRIPTION   = "%s with %d arguments";
    private static final String ERROR_FUNCTION_COLLISION             = "Function collision error for '%s'. A function with the same signature already exists.";
    private static final String ERROR_INVOCATION_NULL                = "Function invocation must not be null.";
    private static final String ERROR_LIBRARY_INSTANCE_NULL          = "Library instance must not be null.";
    private static final String ERROR_NO_FUNCTION_LIBRARY_ANNOTATION = "Provided class has no @FunctionLibrary annotation.";
    private static final String ERROR_NO_MATCHING_FUNCTION_FOUND     = "No matching function found for %s.";

    private static final int DEFAULT_CACHE_SIZE = 10_000;

    private final Map<String, List<FunctionSpecification>> functionIndex;
    @Getter
    private final List<Class<?>>                           registeredLibraries;
    private final int                                      functionCacheSize;
    private final FunctionResultCache                      functionCache;

    public DefaultFunctionBroker() {
        this(DEFAULT_CACHE_SIZE);
    }

    public DefaultFunctionBroker(int functionCacheSize) {
        this(functionCacheSize, new ConcurrentHashMap<>(), new CopyOnWriteArrayList<>());
    }

    private DefaultFunctionBroker(int functionCacheSize,
            Map<String, List<FunctionSpecification>> functionIndex,
            List<Class<?>> registeredLibraries) {
        this.functionIndex       = functionIndex;
        this.registeredLibraries = registeredLibraries;
        this.functionCacheSize   = functionCacheSize;
        this.functionCache       = new FunctionResultCache(functionCacheSize);
    }

    /**
     * Opens a result cache for one configuration. Subclasses retain their own dispatch:
     * this implementation returns {@code this} for them. A stateful subclass must
     * override this method to provide configuration-owned result state.
     */
    @Override
    public FunctionBroker forConfiguration() {
        if (getClass() != DefaultFunctionBroker.class) {
            return this;
        }
        return new DefaultFunctionBroker(functionCacheSize, functionIndex, registeredLibraries);
    }

    @Override
    public void registerConstant(Value value) {
        functionCache.registerConstant(value);
    }

    @Override
    public BoundFunction bindFunction(String functionName, int argumentCount) {
        requireValidName(functionName);
        if (argumentCount < 0) {
            throw new IllegalArgumentException(ERROR_ARGUMENT_COUNT_NEGATIVE);
        }
        val description = ERROR_FUNCTION_BINDING_DESCRIPTION.formatted(functionName, argumentCount);
        val function    = resolveFunction(functionName, argumentCount, description);
        return invocation -> functionCache.evaluate(function, invocation);
    }

    /**
     * Loads a function library from the supplied instance.
     * <p>
     * The instance's class must be annotated with {@link FunctionLibrary}. All
     * methods annotated as functions are registered. Static and instance
     * methods are both supported.
     * <p>
     * Thread-safety: safe to call during initialization. Concurrent loading
     * of different libraries is supported. Collision detection ensures
     * duplicate function signatures are rejected.
     *
     * @param libraryInstance the object instance containing function methods
     * @throws IllegalStateException if the class lacks {@link FunctionLibrary}
     * @throws IllegalArgumentException if {@code libraryInstance} is null or
     * a function signature collides with one already registered
     */
    @Override
    public void load(Object libraryInstance) {
        if (libraryInstance == null) {
            throw new IllegalArgumentException(ERROR_LIBRARY_INSTANCE_NULL);
        }
        val libraryType   = libraryInstance.getClass();
        val libAnnotation = libraryType.getAnnotation(FunctionLibrary.class);

        if (libAnnotation == null) {
            throw new IllegalStateException(ERROR_NO_FUNCTION_LIBRARY_ANNOTATION);
        }

        var libName = libAnnotation.name();
        if (libName.isBlank()) {
            libName = libraryType.getSimpleName();
        }

        for (Method method : libraryType.getDeclaredMethods()) {
            val spec = MethodSignatureProcessor.functionSpecification(libraryInstance, libName, method);
            if (spec != null) {
                loadFunction(spec);
            }
        }
        registeredLibraries.add(libraryType);
    }

    void loadFunction(FunctionSpecification functionSpecification) {
        functionIndex.compute(functionSpecification.functionName(), (functionName, functions) -> {
            val existing = functions != null ? functions : List.<FunctionSpecification>of();

            validateNoCollision(existing, functionSpecification);

            val updated = new ArrayList<FunctionSpecification>(existing);
            updated.add(functionSpecification);
            return List.copyOf(updated);
        });
    }

    private void validateNoCollision(List<FunctionSpecification> existingFunctions, FunctionSpecification newFunction) {
        for (val spec : existingFunctions) {
            if (spec.collidesWith(newFunction)) {
                throw new IllegalArgumentException(ERROR_FUNCTION_COLLISION.formatted(newFunction.functionName()));
            }
        }
    }

    /**
     * Evaluates a function invocation by finding the best matching function
     * specification and applying it.
     * <p>
     * Overload selection uses the function name and argument count. An exact count
     * takes precedence over a match that consumes additional variadic arguments.
     * Equally good matches are ambiguous. The selected function checks argument types.
     * <p>
     * Successful calls can be reused in this configuration. Concurrent misses may
     * run the same pure function independently.
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
            throw new IllegalArgumentException(ERROR_INVOCATION_NULL);
        }
        final FunctionSpecification function;
        try {
            function = resolveFunction(invocation.functionName(), invocation.argumentCount(), invocation);
        } catch (IllegalArgumentException error) {
            return Value.error(error.getMessage());
        }
        return functionCache.evaluate(function, invocation);
    }

    private FunctionSpecification resolveFunction(String functionName, int argumentCount, Object description) {
        val specs = functionIndex.get(functionName);
        if (specs != null) {
            FunctionSpecification bestMatch = null;
            var                   match     = Match.NO_MATCH;
            var                   ambiguous = false;

            for (val spec : specs) {
                val newMatch = spec.matchesArgumentCount(argumentCount);
                if (newMatch.isBetterThan(match)) {
                    match     = newMatch;
                    bestMatch = spec;
                    ambiguous = false;
                } else if (match != Match.NO_MATCH && newMatch == match) {
                    ambiguous = true;
                }
            }

            if (ambiguous) {
                throw new IllegalArgumentException(ERROR_AMBIGUOUS_FUNCTION_MATCH.formatted(description));
            }

            if (bestMatch != null) {
                return bestMatch;
            }
        }

        throw new NoMatchingFunctionException(ERROR_NO_MATCHING_FUNCTION_FOUND.formatted(description));
    }

}

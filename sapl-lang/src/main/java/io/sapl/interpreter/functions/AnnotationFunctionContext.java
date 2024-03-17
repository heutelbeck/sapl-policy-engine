/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.interpreter.functions;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.eclipse.emf.ecore.EObject;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.functions.FunctionLibrarySupplier;
import io.sapl.api.functions.StaticFunctionLibrarySupplier;
import io.sapl.api.interpreter.ExpressionArgument;
import io.sapl.api.interpreter.Val;
import io.sapl.interpreter.InitializationException;
import io.sapl.interpreter.SchemaLoadingUtil;
import io.sapl.interpreter.pip.LibraryEntryMetadata;
import io.sapl.interpreter.validation.IllegalParameterType;
import io.sapl.interpreter.validation.ParameterTypeValidator;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Context to hold functions libraries during policy evaluation.
 */
@NoArgsConstructor
public class AnnotationFunctionContext implements FunctionContext {

    private static final int    VAR_ARGS                                       = -1;
    private static final String UNKNOWN_FUNCTION_ERROR                         = "Unknown function %s";
    private static final String ILLEGAL_NUMBER_OF_PARAMETERS_ERROR             = "Illegal number of parameters. Function expected %d but got %d";
    private static final String CLASS_HAS_NO_FUNCTION_LIBRARY_ANNOTATION_ERROR = "Provided class has no @FunctionLibrary annotation.";
    private static final String ILLEGAL_PARAMETER_FOR_IMPORT_ERROR             = "Function has parameters that are not a Val. Cannot be loaded. Type was: %s.";
    private static final String ILLEGAL_RETURN_TYPE_FOR_IMPORT_ERROR           = "Function does not return a Val. Cannot be loaded. Type was: %s.";
    private static final String MULTIPLE_SCHEMA_ANNOTATIONS_NOT_ALLOWED        = "Function has both a schema and a schemaPath annotation. Multiple schema annotations are not allowed.";

    private final Collection<LibraryDocumentation> documentation = new ConcurrentLinkedQueue<>();
    private final Map<String, FunctionMetadata>    functions     = new ConcurrentHashMap<>();
    private final Map<String, Collection<String>>  libraries     = new ConcurrentHashMap<>();

    private List<String> codeTemplateCache;

    /**
     * Create context from a supplied libraries.
     *
     * @param librariesSupplier       supplies instantiated libraries
     * @param staticLibrariesSupplier supplies libraries contained in utility
     *                                classes with static methods as functions
     * @throws InitializationException if initialization fails.
     */
    public AnnotationFunctionContext(FunctionLibrarySupplier librariesSupplier,
            StaticFunctionLibrarySupplier staticLibrariesSupplier) throws InitializationException {
        loadLibraries(librariesSupplier);
        loadLibraries(staticLibrariesSupplier);
    }

    /**
     * Loads supplied library instances into the context.
     *
     * @param staticLibrariesSupplier supplies libraries contained in utility
     *                                classes with static methods as functions
     * @throws InitializationException if initialization fails.
     */
    public final void loadLibraries(StaticFunctionLibrarySupplier staticLibrariesSupplier)
            throws InitializationException {
        for (var library : staticLibrariesSupplier.get()) {
            loadLibrary(library);
        }
    }

    /**
     * Loads supplied static libraries into the context.
     *
     * @param librariesSupplier supplies instantiated libraries.
     * @throws InitializationException if initialization fails.
     */
    public final void loadLibraries(FunctionLibrarySupplier librariesSupplier) throws InitializationException {
        for (var library : librariesSupplier.get()) {
            loadLibrary(library);
        }
    }

    @Override
    public Val evaluate(EObject location, String function, Val... parameters) {
        var functionTrace = new ExpressionArgument[parameters.length + 1];
        functionTrace[0] = new ExpressionArgument("functionName", Val.of(function));
        for (var parameter = 0; parameter < parameters.length; parameter++) {
            functionTrace[parameter + 1] = new ExpressionArgument("parameter[" + parameter + "]",
                    parameters[parameter]);
        }
        var metadata = functions.get(function);
        if (metadata == null)
            return Val.error(location, UNKNOWN_FUNCTION_ERROR, function).withTrace(FunctionContext.class, false,
                    functionTrace);

        var funParams = metadata.getFunction().getParameters();

        if (metadata.isVarArgsParameters()) {
            return evaluateVarArgsFunction(location, metadata, funParams, parameters).withTrace(FunctionContext.class,
                    false, functionTrace);
        }
        if (metadata.getNumberOfParameters() == parameters.length) {
            return evaluateFixedParametersFunction(location, metadata, funParams, parameters)
                    .withTrace(FunctionContext.class, false, functionTrace);
        }
        return Val.error(location, ILLEGAL_NUMBER_OF_PARAMETERS_ERROR, metadata.getNumberOfParameters(),
                parameters.length).withTrace(FunctionContext.class, false, functionTrace);
    }

    private Val evaluateFixedParametersFunction(EObject location, FunctionMetadata metadata, Parameter[] funParams,
            Val... parameters) {
        for (int i = 0; i < parameters.length; i++) {
            try {
                ParameterTypeValidator.validateType(parameters[i], funParams[i]);
            } catch (IllegalParameterType e) {
                return Val.error(location, e);
            }
        }
        return invokeFunction(metadata, (Object[]) parameters);
    }

    private Val evaluateVarArgsFunction(EObject location, FunctionMetadata metadata, Parameter[] funParams,
            Val... parameters) {
        for (Val parameter : parameters) {
            try {
                ParameterTypeValidator.validateType(parameter, funParams[0]);
            } catch (IllegalParameterType e) {
                return Val.error(location, e);
            }
        }
        return invokeFunction(metadata, (Object[]) new Object[] { parameters });
    }

    private Val invokeFunction(FunctionMetadata metadata, Object... parameters) {
        try {
            return (Val) metadata.getFunction().invoke(metadata.getLibrary(), parameters);
        } catch (Throwable e) {
            return invocationExceptionToError(e, metadata, parameters);
        }
    }

    private Val invocationExceptionToError(Throwable e, LibraryEntryMetadata metadata, Object... parameters) {
        var params = new StringBuilder();
        for (var i = 0; i < parameters.length; i++) {
            params.append(parameters[i]);
            if (i < parameters.length - 2)
                params.append(',');
        }
        return Val.error("Error during evaluation of function %s(%s): %s", metadata.getFunctionName(),
                params.toString(), e.getMessage());
    }

    /**
     * Loads a library into the context.
     *
     * @param library a library instance
     * @throws InitializationException if initialization fails.
     */
    public final void loadLibrary(Object library) throws InitializationException {
        loadLibrary(library, library.getClass());
    }

    public final void loadLibrary(Class<?> libraryType) throws InitializationException {
        loadLibrary(null, libraryType);
    }

    public final void loadLibrary(Object library, Class<?> libraryType) throws InitializationException {
        var libAnnotation = libraryType.getAnnotation(FunctionLibrary.class);

        if (libAnnotation == null) {
            throw new InitializationException(CLASS_HAS_NO_FUNCTION_LIBRARY_ANNOTATION_ERROR);
        }

        String libName = libAnnotation.name();
        if (libName.isEmpty()) {
            libName = libraryType.getSimpleName();
        }
        libraries.put(libName, new HashSet<>());

        LibraryDocumentation libDocs = new LibraryDocumentation(libName, libAnnotation.description());

        for (Method method : libraryType.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Function.class)) {
                importFunction(library, libName, libDocs, method);
            }
        }

        documentation.add(libDocs);
    }

    private void importFunction(Object library, String libName, LibraryDocumentation libMeta, Method method)
            throws InitializationException {
        if (library == null)
            assertMethodIsStatic(method);

        var funAnnotation = method.getAnnotation(Function.class);
        var funName       = funAnnotation.name();
        if (funName.isEmpty())
            funName = method.getName();

        var funSchema       = funAnnotation.schema();
        var funPathToSchema = funAnnotation.pathToSchema();
        if (!funSchema.isEmpty() && !funPathToSchema.isEmpty())
            throw new InitializationException(MULTIPLE_SCHEMA_ANNOTATIONS_NOT_ALLOWED);

        JsonNode processedSchemaDefinition = null;
        if (!funPathToSchema.isEmpty()) {
            processedSchemaDefinition = SchemaLoadingUtil.loadSchemaFromResource(method, funPathToSchema);
        }

        if (!funSchema.isEmpty()) {
            processedSchemaDefinition = SchemaLoadingUtil.loadSchemaFromString(funSchema);
        }

        if (!Val.class.isAssignableFrom(method.getReturnType()))
            throw new InitializationException(ILLEGAL_RETURN_TYPE_FOR_IMPORT_ERROR, method.getReturnType().getName());

        int parameters = method.getParameterCount();
        for (Class<?> parameterType : method.getParameterTypes()) {
            if (parameters == 1 && parameterType.isArray()
                    && Val.class.isAssignableFrom(parameterType.getComponentType())) {
                parameters = VAR_ARGS;
            } else if (!Val.class.isAssignableFrom(parameterType)) {
                throw new InitializationException(ILLEGAL_PARAMETER_FOR_IMPORT_ERROR, parameterType.getName());
            }
        }

        var funMeta = new FunctionMetadata(libName, funName, processedSchemaDefinition, library, parameters, method);
        functions.put(funMeta.fullyQualifiedName(), funMeta);
        libMeta.documentation.put(funMeta.getDocumentationCodeTemplate(), funAnnotation.docs());

        libraries.get(libName).add(funName);
    }

    private void assertMethodIsStatic(Method method) throws InitializationException {
        if (!Modifier.isStatic(method.getModifiers())) {
            throw new InitializationException(
                    "Cannot initialize functions. If no function library instance is provided, the method of a function must be static. "
                            + method.getName()
                            + " is not static. In case your function implementation cannot have the method as static because it depends on library state or injected dependencies, make sure to register the library as an instance instead of a class.");
        }
    }

    @Override
    public Boolean isProvidedFunction(String function) {
        return functions.containsKey(function);
    }

    @Override
    public Collection<LibraryDocumentation> getDocumentation() {
        return Collections.unmodifiableCollection(documentation);
    }

    @Override
    public Collection<String> providedFunctionsOfLibrary(String libraryName) {
        Collection<String> libs = libraries.get(libraryName);
        if (libs != null)
            return libs;
        else
            return new HashSet<>();
    }

    /**
     * Metadata for individual functions.
     */
    @Data
    @AllArgsConstructor
    public static class FunctionMetadata implements LibraryEntryMetadata {

        String libraryName;

        String functionName;

        JsonNode functionSchema;

        Object library;

        int numberOfParameters;

        Method function;

        @Override
        public boolean isVarArgsParameters() {
            return numberOfParameters == VAR_ARGS;
        }

        @Override
        public String getCodeTemplate() {
            var sb = new StringBuilder();
            sb.append(fullyQualifiedName());
            appendParameterList(sb, 0, this::getParameterName);
            if (getNumberOfParameters() == 0)
                sb.append("()");
            return sb.toString();
        }

        @Override
        public String getDocumentationCodeTemplate() {
            var sb = new StringBuilder();
            sb.append(fullyQualifiedName());
            appendParameterList(sb, 0, this::describeParameterForDocumentation);
            if (getNumberOfParameters() == 0)
                sb.append("()");
            return sb.toString();
        }

    }

    @Override
    public Collection<String> getAvailableLibraries() {
        return this.libraries.keySet();
    }

    @Override
    public List<String> getCodeTemplates() {
        if (codeTemplateCache == null) {
            codeTemplateCache = new LinkedList<>();
            for (var entry : functions.entrySet()) {
                codeTemplateCache.add(entry.getValue().getCodeTemplate());
            }
            Collections.sort(codeTemplateCache);
        }
        return codeTemplateCache;
    }

    @Override
    public Map<String, JsonNode> getFunctionSchemas() {
        var schemas = new HashMap<String, JsonNode>();
        for (var entry : functions.entrySet()) {
            schemas.put(entry.getKey(), entry.getValue().functionSchema);
        }
        return schemas;
    }

    @Override
    public Collection<String> getAllFullyQualifiedFunctions() {
        return functions.keySet();
    }

    @Override
    public Map<String, String> getDocumentedCodeTemplates() {
        var documentedCodeTemplates = new HashMap<String, String>();
        for (var entry : functions.entrySet()) {
            var documentationCodeTemplate = entry.getValue().getDocumentationCodeTemplate();
            for (var library : documentation) {
                documentedCodeTemplates.putIfAbsent(library.name, library.description);
                Optional.ofNullable(library.getDocumentation().get(documentationCodeTemplate)).ifPresent(template -> {
                    documentedCodeTemplates.put(entry.getKey(), template);
                    documentedCodeTemplates.put(entry.getValue().getCodeTemplate(), template);
                });
            }
        }
        return documentedCodeTemplates;
    }
}

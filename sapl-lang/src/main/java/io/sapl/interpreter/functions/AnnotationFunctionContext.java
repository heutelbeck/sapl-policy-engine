/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.interpreter.InitializationException;
import io.sapl.interpreter.validation.IllegalParameterType;
import io.sapl.interpreter.validation.ParameterTypeValidator;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Context to hold functions libraries during policy evaluation.
 */
@Slf4j
@NoArgsConstructor
public class AnnotationFunctionContext implements FunctionContext {

	private static final int VAR_ARGS = -1;

	private static final String DOT = ".";

	private static final String UNKNOWN_FUNCTION = "Unknown function %s";

	private static final String ILLEGAL_NUMBER_OF_PARAMETERS = "Illegal number of parameters. Function expected %d but got %d";

	private static final String CLASS_HAS_NO_FUNCTION_LIBRARY_ANNOTATION = "Provided class has no @FunctionLibrary annotation.";

	private static final String ILLEGAL_PARAMETER_FOR_IMPORT = "Function has parameters that are not a Val. Cannot be loaded. Type was: %s.";

	private static final String ILLEGAL_RETURN_TYPE_FOR_IMPORT = "Function does not return a Val. Cannot be loaded. Type was: %s.";

	private final Collection<LibraryDocumentation> documentation = new LinkedList<>();

	private final Map<String, FunctionMetadata> functions = new HashMap<>();

	private final Map<String, Collection<String>> libraries = new HashMap<>();

	/**
	 * Create context from a list of function libraries.
	 * @param libraries list of function libraries @ if loading libraries fails
	 * @throws InitializationException
	 */
	public AnnotationFunctionContext(Object... libraries) throws InitializationException {
		for (Object library : libraries)
			loadLibrary(library);
	}

	@Override
	public Val evaluate(String function, Val... parameters) {
		log.trace("Evaluating function: {}({})", function, parameters);
		var metadata = functions.get(function);
		if (metadata == null)
			return Val.error(UNKNOWN_FUNCTION, function);

		var funParams = metadata.getFunction().getParameters();

		if (metadata.isVarArgs()) {
			return evaluateVarArgsFunction(metadata, funParams, parameters);
		}
		if (metadata.getParameterCardinality() == parameters.length) {
			return evaluateFixedParametersFunction(metadata, funParams, parameters);
		}
		return Val.error(ILLEGAL_NUMBER_OF_PARAMETERS, metadata.getParameterCardinality(), parameters.length);
	}

	private Val evaluateFixedParametersFunction(FunctionMetadata metadata, Parameter[] funParams, Val... parameters) {
		for (int i = 0; i < parameters.length; i++) {
			try {
				ParameterTypeValidator.validateType(parameters[i], funParams[i]);
			}
			catch (IllegalParameterType e) {
				return invokationExceptionToError(e, metadata, (Object[]) parameters);
			}
		}
		return invokeFunction(metadata, (Object[]) parameters);
	}

	private Val evaluateVarArgsFunction(FunctionMetadata metadata, Parameter[] funParams, Val... parameters) {
		for (Val parameter : parameters) {
			try {
				ParameterTypeValidator.validateType(parameter, funParams[0]);
			}
			catch (IllegalParameterType e) {
				return invokationExceptionToError(e, metadata, (Object[]) parameters);
			}
		}
		return invokeFunction(metadata, new Object[] { parameters });
	}

	private Val invokeFunction(FunctionMetadata metadata, Object... parameters) {
		try {
			return (Val) metadata.getFunction().invoke(metadata.getLibrary(), parameters);
		}
		catch (PolicyEvaluationException | IllegalAccessException | InvocationTargetException e) {
			return invokationExceptionToError(e, metadata, parameters);
		}
	}

	private Val invokationExceptionToError(Exception e, FunctionMetadata metadata, Object... parameters) {
		var params = new StringBuilder();
		for (var i = 0; i < parameters.length; i++) {
			params.append(parameters[i]);
			if (i < parameters.length - 2)
				params.append(',');
		}
		return Val.error("Error during evaluation of function %s(%s): %s", metadata.getName(), params.toString(),
				e.getMessage());
	}

	@Override
	public final void loadLibrary(Object library) throws InitializationException {
		Class<?> clazz = library.getClass();

		FunctionLibrary libAnnotation = clazz.getAnnotation(FunctionLibrary.class);

		if (libAnnotation == null) {
			throw new InitializationException(CLASS_HAS_NO_FUNCTION_LIBRARY_ANNOTATION);
		}

		String libName = libAnnotation.name();
		if (libName.isEmpty()) {
			libName = clazz.getSimpleName();
		}
		libraries.put(libName, new HashSet<>());

		LibraryDocumentation libDocs = new LibraryDocumentation(libName, libAnnotation.description(), library);

		for (Method method : clazz.getDeclaredMethods()) {
			if (method.isAnnotationPresent(Function.class)) {
				importFunction(library, libName, libDocs, method);
			}
		}

		documentation.add(libDocs);
	}

	private final void importFunction(Object library, String libName, LibraryDocumentation libMeta, Method method)
			throws InitializationException {
		Function funAnnotation = method.getAnnotation(Function.class);
		String funName = funAnnotation.name();
		if (funName.isEmpty())
			funName = method.getName();

		if (!Val.class.isAssignableFrom(method.getReturnType()))
			throw new InitializationException(ILLEGAL_RETURN_TYPE_FOR_IMPORT, method.getReturnType().getName());

		int parameters = method.getParameterCount();
		for (Class<?> parameterType : method.getParameterTypes()) {
			if (parameters == 1 && parameterType.isArray()
					&& Val.class.isAssignableFrom(parameterType.getComponentType())) {
				parameters = VAR_ARGS;
			}
			else if (!Val.class.isAssignableFrom(parameterType)) {
				throw new InitializationException(ILLEGAL_PARAMETER_FOR_IMPORT, parameterType.getName());
			}
		}
		libMeta.documentation.put(funName, funAnnotation.docs());

		var fullName = fullName(libName, funName);
		FunctionMetadata funMeta = new FunctionMetadata(fullName, library, parameters, method);
		functions.put(fullName, funMeta);

		libraries.get(libName).add(funName);
	}

	@Override
	public Boolean isProvidedFunction(String function) {
		return functions.containsKey(function);
	}

	private static String fullName(String packageName, String methodName) {
		return packageName + DOT + methodName;
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
	public static class FunctionMetadata {

		@NonNull
		String name;

		@NonNull
		Object library;

		int parameterCardinality;

		@NonNull
		Method function;

		public boolean isVarArgs() {
			return parameterCardinality == VAR_ARGS;
		}

	}

	@Override
	public Collection<String> getAvailableLibraries() {
		return this.libraries.keySet();
	}

}

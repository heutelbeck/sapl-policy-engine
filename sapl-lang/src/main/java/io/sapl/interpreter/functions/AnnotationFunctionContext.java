/**
 * Copyright © 2020 Dominic Heutelbeck (dominic@heutelbeck.com)
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
import io.sapl.api.functions.FunctionException;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.Val;
import io.sapl.interpreter.validation.IllegalParameterType;
import io.sapl.interpreter.validation.ParameterTypeValidator;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Context to hold functions libraries during policy evaluation. *
 */
@Slf4j
@NoArgsConstructor
public class AnnotationFunctionContext implements FunctionContext {

	private static final String DOT = ".";
	private static final String UNKNOWN_FUNCTION = "Unknown function %s";
	private static final String ILLEGAL_NUMBER_OF_PARAMETERS = "Illegal number of parameters. Function expected %d but got %d";
	private static final String CLASS_HAS_NO_FUNCTION_LIBRARY_ANNOTATION = "Provided class has no @FunctionLibrary annotation.";
	private static final String ILLEGAL_PARAMETER_FOR_IMPORT = "Function has parameters that are not a Val. Cannot be loaded. Type was: %s.";
	private Collection<LibraryDocumentation> documentation = new LinkedList<>();
	private Map<String, FunctionMetadata> functions = new HashMap<>();
	private Map<String, Collection<String>> libraries = new HashMap<>();

	/**
	 * Create context from a list of function libraries.
	 * 
	 * @param libraries list of function libraries @ if loading libraries fails
	 */
	public AnnotationFunctionContext(Object... libraries) {
		for (Object library : libraries) {
			loadLibrary(library);
		}
	}

	@Override
	public Val evaluate(String function, Val... parameters) {
		log.trace("evaluate {}({})", function, parameters);
		final FunctionMetadata metadata = functions.get(function);
		if (metadata == null) {
			return Val.error(UNKNOWN_FUNCTION, function);
		}
		final Parameter[] funParams = metadata.getFunction().getParameters();
		try {
			if (metadata.getPararmeterCardinality() == -1) {
				// function is a varargs function
				// all args are validated against the same annotation if present
				for (int i = 0; i < parameters.length; i++) {
					ParameterTypeValidator.validateType(parameters[i], funParams[0]);
				}
				return (Val) metadata.getFunction().invoke(metadata.getLibrary(), new Object[] { parameters });
			} else if (metadata.getPararmeterCardinality() == parameters.length) {
				for (int i = 0; i < parameters.length; i++) {
					ParameterTypeValidator.validateType(parameters[i], funParams[i]);
				}
				return (Val) metadata.getFunction().invoke(metadata.getLibrary(), (Object[]) parameters);
			} else {
				return Val.error(ILLEGAL_NUMBER_OF_PARAMETERS, metadata.getPararmeterCardinality(), parameters.length);
			}
		} catch (RuntimeException | IllegalParameterType | IllegalAccessException | InvocationTargetException e) {
			var params = new StringBuilder();
			for (var i = 0; i < parameters.length; i++) {
				params.append(parameters[i]);
				if (i < parameters.length - 2)
					params.append(',');
			}
			return Val.error("Error during evaluation of function %s(%s): %s", function, params.toString(),
					e.getMessage());
		}
	}

	@Override
	public final void loadLibrary(Object library) {
		Class<?> clazz = library.getClass();

		FunctionLibrary libAnnotation = clazz.getAnnotation(FunctionLibrary.class);

		if (libAnnotation == null) {
			throw new FunctionException(CLASS_HAS_NO_FUNCTION_LIBRARY_ANNOTATION);
		}

		String libName = libAnnotation.name();
		if (libName.isEmpty()) {
			libName = clazz.getName();
		}
		libraries.put(libName, new HashSet<>());

		LibraryDocumentation libDocs = new LibraryDocumentation(libName, libAnnotation.description(), library);

		libDocs.setName(libAnnotation.name());
		for (Method method : clazz.getDeclaredMethods()) {
			if (method.isAnnotationPresent(Function.class)) {
				importFunction(library, libName, libDocs, method);
			}
		}
		documentation.add(libDocs);

	}

	private final void importFunction(Object library, String libName, LibraryDocumentation libMeta, Method method) {
		Function funAnnotation = method.getAnnotation(Function.class);
		String funName = funAnnotation.name();
		if (funName.isEmpty()) {
			funName = method.getName();
		}

		int parameters = method.getParameterCount();
		for (Class<?> parameterType : method.getParameterTypes()) {
			if (parameters == 1 && parameterType.isArray()) {
				// functions with a variable number of arguments
				parameters = -1;
			} else if (!Val.class.isAssignableFrom(parameterType)) {
				throw new FunctionException(ILLEGAL_PARAMETER_FOR_IMPORT, parameterType.getName());
			}
		}
		libMeta.documentation.put(funName, funAnnotation.docs());
		FunctionMetadata funMeta = new FunctionMetadata(library, parameters, method);
		functions.put(fullName(libName, funName), funMeta);

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
		if (libs != null) {
			return libs;
		} else {
			return new HashSet<>();
		}
	}

	/**
	 * Metadata for individual functions.
	 */
	@Data
	@AllArgsConstructor
	public static class FunctionMetadata {

		@NonNull
		Object library;

		int pararmeterCardinality;

		@NonNull
		Method function;

	}

}

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionException;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.functions.LibraryDocumentation;
import io.sapl.interpreter.validation.IllegalParameterType;
import io.sapl.interpreter.validation.ParameterTypeValidator;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

@NoArgsConstructor
public class AnnotationFunctionContext implements FunctionContext {

	private static final String DOT = ".";
	private static final String UNKNOWN_FUNCTION = "Unknown function %s";
	private static final String ILLEGAL_NUMBER_OF_PARAMETERS = "Illegal number of parameters. Function expected %d but got %d";
	private static final String CLASS_HAS_NO_FUNCTION_LIBRARY_ANNOTATION = "Provided class has no @FunctionLibrary annotation.";
	private static final String ILLEGAL_PARAMETER_FOR_IMPORT = "Function has parameters that are not a JsonNode. Cannot be loaded. Type was: %s.";

	private Collection<LibraryDocumentation> documentation = new LinkedList<>();
	private Map<String, FunctionMetadata> functions = new HashMap<>();
	private Map<String, Collection<String>> libraries = new HashMap<>();

	public AnnotationFunctionContext(Object... libraries) throws FunctionException {
		for (Object library : libraries) {
			loadLibrary(library);
		}
	}

	@Override
	public JsonNode evaluate(String function, ArrayNode parameters) throws FunctionException {
		FunctionMetadata fun = functions.get(function);
		if (fun == null) {
			throw new FunctionException(String.format(UNKNOWN_FUNCTION, function));
		}
		Object[] args;
		Parameter[] funParams = fun.getFunction().getParameters();
		try {
			if (fun.getPararmeterCardinality() == -1) {
				args = new Object[1];
				JsonNode[] arrayParam = new JsonNode[parameters.size()];
				for (int i = 0; i < parameters.size(); i++) {
					arrayParam[i] = ParameterTypeValidator.validateType(parameters.get(i), funParams[0]);
				}
				args[0] = arrayParam;
			} else if (fun.getPararmeterCardinality() == parameters.size()) {
				args = new Object[parameters.size()];
				for (int i = 0; i < parameters.size(); i++) {
					args[i] = ParameterTypeValidator.validateType(parameters.get(i), funParams[i]);
				}
			} else {
				throw new FunctionException(
						String.format(ILLEGAL_NUMBER_OF_PARAMETERS, fun.getPararmeterCardinality(), parameters.size()));
			}

			return (JsonNode) fun.getFunction().invoke(fun.getLibrary(), args);
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | ClassCastException
				| IllegalParameterType e) {
			throw new FunctionException(e);
		}

	}

	@Override
	public final void loadLibrary(Object library) throws FunctionException {
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

	private final void importFunction(Object library, String libName, LibraryDocumentation libMeta, Method method)
			throws FunctionException {
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
			} else if (!JsonNode.class.isAssignableFrom(parameterType)) {
				throw new FunctionException(String.format(ILLEGAL_PARAMETER_FOR_IMPORT, parameterType.getName()));
			}
		}
		libMeta.documentation.put(funName, funAnnotation.docs());
		FunctionMetadata funMeta = new FunctionMetadata(library, parameters, method);
		functions.put(fullName(libName, funName), funMeta);

		libraries.get(libName).add(funName);
	}

	@Override
	public Boolean provides(String function) {
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
	public Collection<String> functionsInLibrary(String libraryName) {
		if (libraries.containsKey(libraryName)) {
			return libraries.get(libraryName);
		} else {
			return new HashSet<>();
		}
	}

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

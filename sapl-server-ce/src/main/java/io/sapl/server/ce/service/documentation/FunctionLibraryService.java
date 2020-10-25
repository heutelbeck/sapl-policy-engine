package io.sapl.server.ce.service.documentation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import io.sapl.interpreter.functions.LibraryDocumentation;
import io.sapl.spring.pdp.embedded.FunctionLibrariesDocumentation;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for reading {@link FunctionLibrary} instances.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FunctionLibraryService {
	private final FunctionLibrariesDocumentation functionLibrariesDocumentation;

	/**
	 * Gets all available {@link FunctionLibrary}s.
	 * 
	 * @return the instances
	 */
	public Collection<FunctionLibrary> getAll() {
		Collection<LibraryDocumentation> libraryDocumentations = this.getLibraryDocumentations();

		return libraryDocumentations.stream().map((LibraryDocumentation libraryDocumentation) -> FunctionLibraryService
				.toFunctionLibrary(libraryDocumentation)).collect(Collectors.toList());
	}

	private static FunctionLibrary toFunctionLibrary(@NonNull LibraryDocumentation libraryDocumentation) {
		return new FunctionLibrary().setName(libraryDocumentation.getName())
				.setDescription(libraryDocumentation.getDescription())
				.setFunctionDocumentation(libraryDocumentation.getDocumentation());
	}

	/**
	 * Gets the amount of available {@link FunctionLibrary}s.
	 * 
	 * @return the amount
	 */
	public long getAmount() {
		return this.getLibraryDocumentations().size();
	}

	/**
	 * Gets a single function library by its name.
	 * 
	 * @param functionLibraryName the name of the function library
	 * @return the function library
	 */
	public FunctionLibrary getByName(@NonNull String functionLibraryName) {
		Optional<FunctionLibrary> functionLibraryAsOptional = this.getLibraryDocumentations().stream()
				.filter((LibraryDocumentation libraryDocumentation) -> libraryDocumentation.getName()
						.equals(functionLibraryName))
				.map((LibraryDocumentation libraryDocumentation) -> FunctionLibraryService
						.toFunctionLibrary(libraryDocumentation))
				.findFirst();
		if (!functionLibraryAsOptional.isPresent()) {
			throw new IllegalStateException(
					String.format("function library with name %s is not available", functionLibraryName));
		}

		return functionLibraryAsOptional.get();
	}

	/**
	 * Gets the functions of a specific function library.
	 * 
	 * @param functionLibraryName the name of the function library
	 * @return the functions
	 */
	public Collection<Function> getFunctionsOfLibrary(@NonNull String functionLibraryName) {
		FunctionLibrary functionLibrary = this.getByName(functionLibraryName);
		Map<String, String> functionDocumentation = functionLibrary.getFunctionDocumentation();

		List<Function> functions = new ArrayList<Function>(functionDocumentation.size());
		for (String name : functionDocumentation.keySet()) {
			String documentation = functionDocumentation.get(name);

			Function function = new Function().setName(name).setDocumentation(documentation);
			functions.add(function);
		}

		return functions;
	}

	private Collection<LibraryDocumentation> getLibraryDocumentations() {
		if (this.functionLibrariesDocumentation == null) {
			log.warn("cannot get documentation for function libraries (configured bean)");
			return Collections.emptyList();
		}

		return this.functionLibrariesDocumentation.getDocumentation();
	}
}

package io.sapl.server.ce.service.documentation;

import java.util.Map;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * A function library.
 */
@Data
@Accessors(chain = true)
public class FunctionLibrary {
	/**
	 * The name of the library.
	 */
	private String name;

	/**
	 * The description of the library.
	 */
	private String description;

	/**
	 * The documentation of the functions of the library.
	 */
	private Map<String, String> functionDocumentation;

	/**
	 * Gets the amount of available functions.
	 * 
	 * @return the amount
	 */
	public int getAmountOfFunctions() {
		if (this.functionDocumentation == null) {
			return 0;
		}

		return this.functionDocumentation.size();
	}
}

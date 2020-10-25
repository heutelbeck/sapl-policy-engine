package io.sapl.server.ce.service.documentation;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * A single function of a {@link FunctionLibrary}.
 */
@Data
@Accessors(chain = true)
public class Function {
	/**
	 * The function name.
	 */
	private String name;

	/**
	 * The documentation of the function.
	 */
	private String documentation;
}

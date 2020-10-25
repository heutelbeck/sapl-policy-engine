package io.sapl.server.ce.service.documentation;

import java.util.Map;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * Model for a policy information point (PIP).
 */
@Data
@Accessors(chain = true)
public class PolicyInformationPoint {
	/**
	 * The name of the PIP.
	 */
	private String name;

	/**
	 * The description of the PIP.
	 */
	private String description;

	/**
	 * The documentation of the functions of the PIP.
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

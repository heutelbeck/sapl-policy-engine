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
package io.sapl.springdatacommon.services;

import org.springframework.security.access.AccessDeniedException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.experimental.UtilityClass;

/**
 * Utility class for comparing JSON node structures.
 */
@UtilityClass
public class JsonNodeStructure {

	/**
	 * Compares the structure of two JSON nodes.
	 *
	 * @param obligation The JSON node representing the obligation.
	 * @param template   The JSON node representing the template.
	 * @return True if the structure of the obligation matches that of the template,
	 *         otherwise false.
	 * @throws AccessDeniedException If the obligation does not satisfy the required
	 *                               structure.
	 */
	public static boolean compare(JsonNode obligation, JsonNode template) {
		try {
			if (obligation.getNodeType() != template.getNodeType()) {
				return false;
			}
		} catch (NullPointerException e) {
			throw getAccessDeniedException(obligation, template);
		}

		if (obligation.isObject()) {

			return obligationObjectFieldsExistsInTemplate(obligation, template);
		} else if (obligation.isArray()) {

			return obligationsArrayExistsInTemplate(obligation, template);
		} else if ((obligation.isTextual() || obligation.isNumber() || obligation.isBoolean())
				&& !obligation.asText().isEmpty()) {
			return true;
		}

		return false;
	}

	private boolean obligationObjectFieldsExistsInTemplate(JsonNode obligation, JsonNode template) {

		var obligationObject = (ObjectNode) obligation;
		var templateObject = (ObjectNode) template;

		var fieldsOfTemplateObject = templateObject.fields();

		while (fieldsOfTemplateObject.hasNext()) {

			var templateField = fieldsOfTemplateObject.next();
			var templateFieldName = templateField.getKey();
			var templateFieldValue = templateField.getValue();

			if (!obligationObject.has(templateFieldName)) {
				return false;
			}

			var obligationFieldValue = obligationObject.get(templateFieldName);

			if (!compare(obligationFieldValue, templateFieldValue)) {
				return false;
			}
		}

		return true;
	}

	private boolean obligationsArrayExistsInTemplate(JsonNode obligation, JsonNode template) {

		var obligationArray = (ArrayNode) obligation;
		var templateArray = (ArrayNode) template;

		/**
		 * An object in the array means that the user only wants to validate the type
		 * and there can be as many objects of this type in the array as the user wants.
		 * If more than one object has been defined in the template, the user wants
		 * exactly the same number of objects to exist in the obligation.
		 */
		var templateArrayValuesAmountEqualsOne = templateArray.size() == 1;

		for (int i = 0; i < templateArray.size(); i++) {

			var templateIndex = templateArrayValuesAmountEqualsOne ? 0 : i;

			try {
				if (!compare(obligationArray.get(i), templateArray.get(templateIndex))) {
					return false;
				}
			} catch (NullPointerException e) {
				throw getAccessDeniedException(obligation, template);
			}
		}

		return true;
	}

	private AccessDeniedException getAccessDeniedException(JsonNode obligation, JsonNode template) {
		return new AccessDeniedException("Obligation does not satisfy the required structure. \n\n Obligation: "
				+ obligation.toPrettyString() + " \n Template:   " + template.toPrettyString());
	}

}

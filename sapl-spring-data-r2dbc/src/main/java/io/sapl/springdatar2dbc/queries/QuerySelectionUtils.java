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
package io.sapl.springdatar2dbc.queries;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import com.fasterxml.jackson.databind.node.ArrayNode;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class QuerySelectionUtils {

	private static final String DOT = ".";
	private static final String SPACE = " ";
	private static final String TYPE = "type";
	private static final String ASTERISK = "*";
	private static final String FROM = " from ";
	private static final String ALIAS = "alias";
	private static final String EMPTY_STRING = "";
	private static final String COLUMNS = "columns";
	private static final String COMMA_WITH_SPACE = ", ";
	private static final String WHITELIST = "whitelist";
	private static final String SELECT_LOWERCASE = "select ";
	private static final String SELECT_WITH_SPACE = "SELECT ";
	private static final String FROM_XXXXX_WHERE = " FROM XXXXX WHERE ";
	private static final String QUERY_LOG = "Several selection nodes detected. Only one selection node is possible, so the first node found is used: {}";

	public static <T> String createSelectionPartForMethodNameQuery(ArrayNode selections, Class<T> domainType) {

		var selectionPart = createSelectionPart(selections, domainType);

		if (selectionPart.isEmpty()) {
			return SELECT_WITH_SPACE + ASTERISK + FROM_XXXXX_WHERE;
		}

		var queryBuilder = new StringBuilder();

		return queryBuilder.append(SELECT_WITH_SPACE).append(selectionPart).append(FROM_XXXXX_WHERE).toString();

	}

	public <T> String createSelectionPartForAnnotation(String query, ArrayNode selections, Class<T> domainType) {
		if (selections.isEmpty()) {
			return query;
		}

		var selectIndex = query.toLowerCase().indexOf(SELECT_LOWERCASE);
		var fromIndex = query.toLowerCase().indexOf(FROM);

		var fieldList = QuerySelectionUtils.createSelectionPart(selections, domainType);
		var selectionToReplace = query.substring(selectIndex + SELECT_LOWERCASE.length(), fromIndex);

		return query.replace(selectionToReplace, fieldList);
	}

	public static <T> String createSelectionPart(ArrayNode selections, Class<T> domainType) {
		if (selections.isEmpty()) {
			return EMPTY_STRING;
		}

		if (selections.size() > 1) {
			log.info(QUERY_LOG, selections.get(0).toPrettyString());
		}

		var selection = selections.get(0);
		var fieldList = new ArrayList<String>();
		var elements = selection.get(COLUMNS).elements();
		var alias = EMPTY_STRING;

		if (selection.has(ALIAS) && !selection.get(ALIAS).asText().isEmpty()) {
			alias = selection.get(ALIAS).asText();
		}

		while (elements.hasNext()) {
			var element = elements.next();
			fieldList.add(element.asText());
		}

		if (WHITELIST.equals(selection.get(TYPE).asText())) {
			return handleWhitelist(fieldList.iterator(), alias);
		} else {
			return handleBlacklist(fieldList, domainType, alias);
		}
	}

	private static <T> String handleBlacklist(List<String> blackList, Class<T> domainType, String alias) {
		var fields = Arrays.asList(domainType.getDeclaredFields());
		var finalFieldList = new ArrayList<String>();
		var stringBuilder = new StringBuilder();
		stringBuilder.append(SPACE);

		for (Field field : fields) {
			String name = field.getName();
			finalFieldList.add(name);
		}

		for (String field : blackList) {
			if (finalFieldList.contains(field)) {
				finalFieldList.remove(field);
			}
		}

		for (int i = 0; i < finalFieldList.size(); i++) {

			if (!alias.equals(EMPTY_STRING)) {
				stringBuilder.append(alias + DOT);
			}

			stringBuilder.append(finalFieldList.get(i));

			if (finalFieldList.size() - 1 != i) {
				stringBuilder.append(COMMA_WITH_SPACE);
			}
		}

		return stringBuilder.append(SPACE).toString();
	}

	private static String handleWhitelist(Iterator<String> whiteListIterator, String alias) {
		var stringBuilder = new StringBuilder();

		while (whiteListIterator.hasNext()) {

			if (!alias.equals(EMPTY_STRING)) {
				stringBuilder.append(alias).append(DOT);
			}

			stringBuilder.append(whiteListIterator.next());

			if (whiteListIterator.hasNext()) {
				stringBuilder.append(COMMA_WITH_SPACE);
			}
		}

		return stringBuilder.toString();
	}

}

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
package io.sapl.benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.generator.DomainData;
import io.sapl.generator.DomainSubject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

@Data
@Slf4j
public class PolicyGenerator {

	private static final int DEFAULT_BUFFER = 50;

	private static final JsonNode EMPTY_NODE = JsonNodeFactory.instance.objectNode();
	private final double emptySubNodeProbability;
	private final double emptySubProbability;

	private final PolicyGeneratorConfiguration config;
	private final DomainData domainData;

	public PolicyGenerator(PolicyGeneratorConfiguration config, DomainData domainData) {
		this.config = config;
		this.domainData = domainData;
		this.emptySubNodeProbability = domainData.getProbabilityEmptySubscriptionNode();
		this.emptySubProbability = domainData.getProbabilityEmptySubscription();
	}

	private String generatePolicyString(String name) {
		final int numberOfVariables = config.getLogicalVariableCount();
		final int numberOfConnectors = numberOfVariables - 1;
		final int poolSize = config.getVariablePoolCount();

		final double negationChance = config.getNegationProbability();
		final double bracketChance = config.getBracketProbability();
		final double conjunctionChance = config.getConjunctionProbability();

		StringBuilder statement = new StringBuilder(DEFAULT_BUFFER).append("policy \"").append(name).append("\"")
				.append(System.lineSeparator()).append("permit ");

		int open = 0;
		for (int j = 0; j < numberOfVariables; ++j) {
			if (roll() <= negationChance) {
				statement.append('!');
			}
			while (roll() <= bracketChance) {
				statement.append('(');
				++open;
			}
			statement.append(getIdentifier(roll(poolSize)));
			double chance = 1.0 / (numberOfVariables - j);
			while (open > 0 && roll() < chance) {
				statement.append(')');
				--open;
			}
			if (j < numberOfConnectors) {
				if (roll() <= conjunctionChance) {
					statement.append(" & ");
				} else {
					statement.append(" | ");
				}
			}
		}

		return statement.toString();
	}

	private static String getIdentifier(int index) {
		return "resource.x" + index;
	}

	private double roll() {
		return domainData.roll();
	}

	private int roll(int supremum) {
		return domainData.getDice().nextInt(supremum);
	}

	public void generatePolicies(String subfolder) throws FileNotFoundException, UnsupportedEncodingException {
		String path = config.getPath() + subfolder + "/";

		File folder = new File(path);
		if (folder.mkdirs()) {
			final File[] files = folder.listFiles();
			if (files != null) {
				for (File file : files) {
					if (file.getName().endsWith("sapl") && !file.delete()) {
						log.error("failed to delete: {}", file.getAbsolutePath());
					}
				}
			}
		}
		for (int i = 0; i < config.getPolicyCount(); i++) {
			String name = "p_" + i;
			try (PrintWriter writer = new PrintWriter(path + name + ".sapl", StandardCharsets.UTF_8.name())) {
				writer.println(generatePolicyString(name));
			}
		}

	}

	public AuthorizationSubscription createStructuredRandomSubscription() {
		double roll = roll();
		if (roll >= emptySubProbability) {
			log.trace("dice rolled {} - higher than {} -> EMPTY SUB", roll, emptySubProbability);
			return createEmptySubscription();
		}
		log.trace("dice rolled {} - lower than {}", roll, emptySubProbability);
		return createSubscription(getRandomSub(), getRandomAction(), getRandomResource());
	}

	private JsonNode buildSubjectNode(DomainSubject domainSubject) {
		ObjectNode subject = JsonNodeFactory.instance.objectNode();
		ArrayNode authorityNode = JsonNodeFactory.instance.arrayNode();
		for (String subjectAuthority : domainSubject.getSubjectAuthorities()) {
			authorityNode.add(subjectAuthority);
		}
		subject.set("authorities", authorityNode);
		subject.put("name", domainSubject.getSubjectName());

		return subject;
	}

	public <T> T getRandomElement(List<T> list) {
		return list.get(roll(list.size()));
	}

	private JsonNode getRandomResource() {
		double roll = roll();
		if (roll >= emptySubNodeProbability) {
			log.trace("dice rolled {} - higher than {} -> EMPTY RESOURCE", roll, emptySubNodeProbability);
			return EMPTY_NODE;
		}
		log.trace("dice rolled {} - lower than {}", roll, emptySubNodeProbability);
		return JsonNodeFactory.instance.textNode(getRandomElement(domainData.getDomainResources()).getResourceName());
	}

	private JsonNode getRandomAction() {
		double roll = roll();
		if (roll >= emptySubNodeProbability) {
			log.trace("dice rolled {} - higher than {} -> EMPTY ACTION", roll, emptySubNodeProbability);
			return EMPTY_NODE;
		}
		log.trace("dice rolled {} - lower than {}", roll, emptySubNodeProbability);
		return JsonNodeFactory.instance.textNode(getRandomElement(domainData.getDomainActions()));
	}

	private JsonNode getRandomSub() {
		double roll = roll();
		if (roll >= emptySubNodeProbability) {
			log.trace("dice rolled {} - higher than {} -> EMPTY SUBJECT", roll, emptySubNodeProbability);
			return EMPTY_NODE;
		}
		log.trace("dice rolled {} - lower than {}", roll, emptySubNodeProbability);
		DomainSubject domainSubject = getRandomElement(domainData.getDomainSubjects());
		return buildSubjectNode(domainSubject);
	}

	public AuthorizationSubscription createEmptySubscription() {
		return createSubscription(EMPTY_NODE, EMPTY_NODE, EMPTY_NODE);
	}

	private AuthorizationSubscription createSubscription(JsonNode subject, JsonNode action, JsonNode resource) {
		return new AuthorizationSubscription(subject, action, resource, EMPTY_NODE);
	}

	public Collection<String> getVariables() {
		HashSet<String> variables = new HashSet<>();
		for (int i = 0; i < config.getVariablePoolCount(); i++) {
			variables.add("x" + i);
		}
		return variables;
	}

	public AuthorizationSubscription createFullyRandomSubscription() {
		ObjectNode resource = JsonNodeFactory.instance.objectNode();
		for (String var : getVariables()) {
			resource = resource.put(var, roll() < config.getFalseProbability() ? false : true);
		}
		return new AuthorizationSubscription(NullNode.getInstance(), NullNode.getInstance(), resource,
				NullNode.getInstance());
	}

}

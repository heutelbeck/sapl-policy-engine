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
package io.sapl.prp.resources;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.IOUtils;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.SAPLInterpreter;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.prp.ParsedDocumentIndex;
import io.sapl.api.prp.PolicyRetrievalPoint;
import io.sapl.api.prp.PolicyRetrievalResult;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.prp.inmemory.simple.SimpleParsedDocumentIndex;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
public class ResourcesPolicyRetrievalPoint implements PolicyRetrievalPoint {

	private static final String DEFAULT_POLICIES_PATH = "/policies";

	private static final String POLICY_FILE_GLOB_PATTERN = "*.sapl";

	private static final String POLICY_FILE_SUFFIX = ".sapl";

	private ParsedDocumentIndex parsedDocIdx;

	public ResourcesPolicyRetrievalPoint() throws IOException, URISyntaxException, PolicyEvaluationException {
		this(DEFAULT_POLICIES_PATH, new SimpleParsedDocumentIndex());
	}

	public ResourcesPolicyRetrievalPoint(@NonNull String policyPath, @NonNull ParsedDocumentIndex parsedDocumentIndex)
			throws IOException, URISyntaxException, PolicyEvaluationException {
		this(ResourcesPolicyRetrievalPoint.class, policyPath, parsedDocumentIndex);
	}

	public ResourcesPolicyRetrievalPoint(@NonNull Class<?> clazz, @NonNull String policyPath,
			@NonNull ParsedDocumentIndex parsedDocumentIndex)
			throws IOException, URISyntaxException, PolicyEvaluationException {

		URL policyFolderUrl = clazz.getResource(policyPath);
		if (policyFolderUrl == null) {
			throw new PolicyEvaluationException("Policy folder not found. Path:" + policyPath);
		}

		this.parsedDocIdx = parsedDocumentIndex;

		if ("jar".equals(policyFolderUrl.getProtocol())) {
			readPoliciesFromJar(policyFolderUrl);
		}
		else {
			readPoliciesFromDirectory(policyFolderUrl);
		}
		this.parsedDocIdx.setLiveMode();
	}

	private void readPoliciesFromJar(URL policiesFolderUrl) throws PolicyEvaluationException {
		LOGGER.debug("reading policies from jar {}", policiesFolderUrl);
		final String[] jarPathElements = policiesFolderUrl.toString().split("!");
		final String jarFilePath = jarPathElements[0].substring("jar:file:".length());
		final StringBuilder policiesDirPath = new StringBuilder();
		for (int i = 1; i < jarPathElements.length; i++) {
			policiesDirPath.append(jarPathElements[i]);
		}
		if (policiesDirPath.charAt(0) == File.separatorChar) {
			policiesDirPath.deleteCharAt(0);
		}
		final String policiesDirPathStr = policiesDirPath.toString();

		final SAPLInterpreter interpreter = new DefaultSAPLInterpreter();

		try (ZipFile zipFile = new ZipFile(jarFilePath)) {
			Enumeration<? extends ZipEntry> e = zipFile.entries();

			while (e.hasMoreElements()) {
				ZipEntry entry = e.nextElement();
				if (!entry.isDirectory() && entry.getName().startsWith(policiesDirPathStr)
						&& entry.getName().endsWith(POLICY_FILE_SUFFIX)) {
					LOGGER.debug("load: {}", entry.getName());
					BufferedInputStream bis = new BufferedInputStream(zipFile.getInputStream(entry));
					String fileContentsStr = IOUtils.toString(bis, StandardCharsets.UTF_8);
					bis.close();
					final SAPL saplDocument = interpreter.parse(fileContentsStr);
					this.parsedDocIdx.put(entry.getName(), saplDocument);
				}
			}
		}
		catch (IOException e) {
			LOGGER.error("Error while reading config from jar", e);
		}
	}

	private void readPoliciesFromDirectory(URL policiesFolderUrl)
			throws IOException, URISyntaxException, PolicyEvaluationException {
		LOGGER.debug("reading policies from directory {}", policiesFolderUrl);
		final SAPLInterpreter interpreter = new DefaultSAPLInterpreter();
		Path policiesDirectoryPath = Paths.get(policiesFolderUrl.toURI());
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(policiesDirectoryPath, POLICY_FILE_GLOB_PATTERN)) {
			for (Path filePath : stream) {
				LOGGER.debug("load: {}", filePath);
				final SAPL saplDocument = interpreter.parse(Files.newInputStream(filePath));
				this.parsedDocIdx.put(filePath.toString(), saplDocument);
			}
		}
	}

	@Override
	public Flux<PolicyRetrievalResult> retrievePolicies(AuthorizationSubscription authzSubscription,
			FunctionContext functionCtx, Map<String, JsonNode> variables) {
		return Flux.from(parsedDocIdx.retrievePolicies(authzSubscription, functionCtx, variables))
				.doOnNext(this::logMatching);
	}

	private void logMatching(PolicyRetrievalResult result) {
		if (result.getMatchingDocuments().isEmpty()) {
			LOGGER.trace("|-- Matching documents: NONE");
		}
		else {
			LOGGER.trace("|-- Matching documents:");
			for (SAPL doc : result.getMatchingDocuments()) {
				LOGGER.trace("| |-- * {} ({})", doc.getPolicyElement().getSaplName(),
						doc.getPolicyElement().getClass().getName());
			}
		}
		LOGGER.trace("|");
	}

}

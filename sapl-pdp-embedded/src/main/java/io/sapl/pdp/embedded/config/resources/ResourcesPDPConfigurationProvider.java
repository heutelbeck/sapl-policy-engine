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
package io.sapl.pdp.embedded.config.resources;

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
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.pdp.PDPConfigurationException;
import io.sapl.api.pdp.PolicyDecisionPointConfiguration;
import io.sapl.interpreter.combinators.DocumentsCombinator;
import io.sapl.pdp.embedded.config.PDPConfigurationProvider;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
public class ResourcesPDPConfigurationProvider implements PDPConfigurationProvider {

	private static final String DEFAULT_CONFIG_PATH = "/policies";

	private static final String CONFIG_FILE_GLOB_PATTERN = "pdp.json";

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private PolicyDecisionPointConfiguration config;

	public ResourcesPDPConfigurationProvider() throws PDPConfigurationException, IOException, URISyntaxException {
		this(DEFAULT_CONFIG_PATH);
	}

	public ResourcesPDPConfigurationProvider(@NonNull String configPath)
			throws PDPConfigurationException, IOException, URISyntaxException {
		this(ResourcesPDPConfigurationProvider.class, configPath);
	}

	public ResourcesPDPConfigurationProvider(@NonNull Class<?> clazz, @NonNull String configPath)
			throws PDPConfigurationException, IOException, URISyntaxException {
		URL configFolderUrl = clazz.getResource(configPath);
		if (configFolderUrl == null) {
			throw new PDPConfigurationException("Config folder not found. Path:" + configPath + " - URL: null");
		}

		if ("jar".equals(configFolderUrl.getProtocol())) {
			readConfigFromJar(configFolderUrl);
		}
		else {
			readConfigFromDirectory(configFolderUrl);
		}

		if (this.config == null) {
			LOGGER.debug("config is null - using default config");
			this.config = new PolicyDecisionPointConfiguration();
		}
	}

	private void readConfigFromJar(URL configFolderUrl) {
		LOGGER.debug("reading config from jar {}", configFolderUrl);
		final String[] jarPathElements = configFolderUrl.toString().split("!");
		final String jarFilePath = jarPathElements[0].substring("jar:file:".length());
		final StringBuilder dirPath = new StringBuilder();
		for (int i = 1; i < jarPathElements.length; i++) {
			dirPath.append(jarPathElements[i]);
		}
		if (dirPath.charAt(0) == File.separatorChar) {
			dirPath.deleteCharAt(0);
		}
		final String configFilePath = dirPath.append(File.separatorChar).append(CONFIG_FILE_GLOB_PATTERN).toString();

		try (ZipFile zipFile = new ZipFile(jarFilePath)) {
			Enumeration<? extends ZipEntry> e = zipFile.entries();

			while (e.hasMoreElements()) {
				ZipEntry entry = e.nextElement();
				if (!entry.isDirectory() && entry.getName().equals(configFilePath)) {
					LOGGER.debug("load: {}", entry.getName());
					BufferedInputStream bis = new BufferedInputStream(zipFile.getInputStream(entry));
					String fileContentsStr = IOUtils.toString(bis, StandardCharsets.UTF_8);
					bis.close();
					this.config = MAPPER.readValue(fileContentsStr, PolicyDecisionPointConfiguration.class);
					break;
				}
			}
		}
		catch (IOException e) {
			LOGGER.error("Error while reading config from jar", e);
		}
	}

	private void readConfigFromDirectory(URL configFolderUrl) throws IOException, URISyntaxException {
		LOGGER.debug("reading config from directory {}", configFolderUrl);
		Path configDirectoryPath = Paths.get(configFolderUrl.toURI());
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(configDirectoryPath, CONFIG_FILE_GLOB_PATTERN)) {
			for (Path filePath : stream) {
				LOGGER.debug("load: {}", filePath);
				this.config = MAPPER.readValue(filePath.toFile(), PolicyDecisionPointConfiguration.class);
				break;
			}
		}
	}

	public ResourcesPDPConfigurationProvider(PolicyDecisionPointConfiguration config) {
		this.config = config;
	}

	@Override
	public Flux<DocumentsCombinator> getDocumentsCombinator() {
		return Flux.just(config.getAlgorithm()).map(algorithm -> {
			LOGGER.trace("|-- Current PDP config: combining algorithm = {}", algorithm);
			return convert(algorithm);
		});
	}

	@Override
	public Flux<Map<String, JsonNode>> getVariables() {
		return Flux.just((Map<String, JsonNode>) config.getVariables())
				.doOnNext(variables -> LOGGER.trace("|-- Current PDP config: variables = {}", variables));
	}

}

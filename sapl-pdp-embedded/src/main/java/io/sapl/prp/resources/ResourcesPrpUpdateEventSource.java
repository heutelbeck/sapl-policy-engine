/*
 * Copyright © 2017-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import io.sapl.interpreter.InitializationException;
import io.sapl.interpreter.SAPLInterpreter;
import io.sapl.prp.PrpUpdateEvent;
import io.sapl.prp.PrpUpdateEvent.Type;
import io.sapl.prp.PrpUpdateEvent.Update;
import io.sapl.prp.PrpUpdateEventSource;
import io.sapl.util.JarUtil;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
public class ResourcesPrpUpdateEventSource implements PrpUpdateEventSource {

	private static final String POLICY_FILE_SUFFIX = ".sapl";

	private static final String POLICY_FILE_GLOB_PATTERN = "*" + POLICY_FILE_SUFFIX;

	private final SAPLInterpreter interpreter;

	private final PrpUpdateEvent initializingPrpUpdate;

	public ResourcesPrpUpdateEventSource(String policyPath, SAPLInterpreter interpreter)
			throws InitializationException {
		this(ResourcesPrpUpdateEventSource.class, policyPath, interpreter);
	}

	public ResourcesPrpUpdateEventSource(@NonNull Class<?> clazz, @NonNull String policyPath,
			@NonNull SAPLInterpreter interpreter) throws InitializationException {
		this.interpreter = interpreter;
		log.info("Loading a static set of policies from the bundled resources");
		initializingPrpUpdate = readPolicies(JarUtil.inferUrlOfResourcesPath(clazz, policyPath));
	}

	private PrpUpdateEvent readPolicies(URL policyFolderUrl) throws InitializationException {
		try {
			if ("jar".equals(policyFolderUrl.getProtocol()))
				return readPoliciesFromJar(policyFolderUrl);
			return readPoliciesFromDirectory(policyFolderUrl);
		}
		catch (IOException | URISyntaxException e) {
			throw (InitializationException) new InitializationException("Failed to read policies").initCause(e);
		}
	}

	private PrpUpdateEvent readPoliciesFromJar(URL policiesFolderUrl) throws IOException {
		log.debug("reading policies from jar {}", policiesFolderUrl);
		var pathOfJar = JarUtil.getJarFilePath(policiesFolderUrl);
		try (var jarFile = new ZipFile(pathOfJar)) {
			var updates = jarFile.stream().filter(this::isSAPLDocumentWithinPath)
					.peek(entry -> log.info("load SAPL document: {}", entry.getName()))
					.map(entry -> JarUtil.readStringFromZipEntry(jarFile, entry))
					.map(this::parseAndCreatePublicationUpdate).collect(Collectors.toList());
			return new PrpUpdateEvent(updates);
		}
	}

	private boolean isSAPLDocumentWithinPath(ZipEntry zipEntry) {
		return !zipEntry.isDirectory() && zipEntry.getName().endsWith(POLICY_FILE_SUFFIX);
	}

	private Update parseAndCreatePublicationUpdate(String rawDocument) {
		return new Update(Type.PUBLISH, interpreter.parse(rawDocument), rawDocument);
	}

	private PrpUpdateEvent readPoliciesFromDirectory(URL policiesFolderUrl) throws IOException, URISyntaxException {
		log.debug("reading policies from directory {}", policiesFolderUrl);
		try (var directoryStream = Files.newDirectoryStream(Paths.get(policiesFolderUrl.toURI()),
				POLICY_FILE_GLOB_PATTERN)) {
			var updates = StreamSupport.stream(directoryStream.spliterator(), false)
					.peek(path -> log.info("load SAPL document: {}", path))
					.map(ResourcesPrpUpdateEventSource::readFileAsString).map(this::parseAndCreatePublicationUpdate)
					.collect(Collectors.toList());
			return new PrpUpdateEvent(updates);
		}
	}

	@SneakyThrows
	private static String readFileAsString(Path path) {
		return Files.readString(path);
	}

	@Override
	public void dispose() {
		// NOP nothing to dispose of
	}

	@Override
	public Flux<PrpUpdateEvent> getUpdates() {
		return Flux.just(initializingPrpUpdate);
	}

}

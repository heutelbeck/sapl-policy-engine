package io.sapl.pdp.embedded.config.resources;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.interpreter.SAPLInterpreter;
import io.sapl.api.pdp.PolicyDecisionPointConfiguration;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.combinators.DocumentsCombinator;
import io.sapl.api.pdp.PDPConfigurationException;
import io.sapl.pdp.embedded.config.PDPConfigurationProvider;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
public class ResourcesPDPConfigurationProvider implements PDPConfigurationProvider {

	private static final String DEFAULT_CONFIG_PATH = "/policies";

	private static final String CONFIG_FILE_GLOB_PATTERN = "pdp.json";

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final SAPLInterpreter interpreter = new DefaultSAPLInterpreter();

	private PolicyDecisionPointConfiguration config;

	public ResourcesPDPConfigurationProvider()
			throws PDPConfigurationException, IOException, URISyntaxException {
		this(DEFAULT_CONFIG_PATH);
	}

	public ResourcesPDPConfigurationProvider(@NonNull String configPath)
			throws PDPConfigurationException, IOException, URISyntaxException {
		this(ResourcesPDPConfigurationProvider.class, configPath);
	}

	public ResourcesPDPConfigurationProvider(@NonNull Class<?> clazz,
			@NonNull String configPath)
			throws PDPConfigurationException, IOException, URISyntaxException {
		URL configFolderUrl = clazz.getResource(configPath);

		if (configFolderUrl == null) {
			throw new PDPConfigurationException("Config folder not found. Path:"
					+ configPath + " - URL: " + configFolderUrl);
		}

		Path path;
		FileSystem fs = null;
		try {
			if (configFolderUrl.getProtocol().equals("jar")) {
				final Map<String, String> env = new HashMap<>();
				final String[] array = configFolderUrl.toString().split("!");
				fs = FileSystems.newFileSystem(URI.create(array[0]), env);
				path = fs.getPath(array[1]);
			}
			else {
				path = Paths.get(configFolderUrl.toURI());
			}
			LOGGER.info("current path: {}", path);
			try (DirectoryStream<Path> stream = Files.newDirectoryStream(path,
					CONFIG_FILE_GLOB_PATTERN)) {
				for (Path filePath : stream) {
					LOGGER.info("load: {}", filePath);
					this.config = MAPPER.readValue(filePath.toFile(),
							PolicyDecisionPointConfiguration.class);
					break;
				}
				if (this.config == null) {
					this.config = new PolicyDecisionPointConfiguration();
				}
			}
		}
		finally {
			if (fs != null) {
				fs.close();
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
			return convert(algorithm, interpreter);
		});
	}

	@Override
	public Flux<Map<String, JsonNode>> getVariables() {
		return Flux.just(config.getVariables()).doOnNext(variables -> LOGGER
				.trace("|-- Current PDP config: variables = {}", variables));
	}

}

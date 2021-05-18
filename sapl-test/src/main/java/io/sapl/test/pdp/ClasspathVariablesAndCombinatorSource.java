package io.sapl.test.pdp;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.grammar.sapl.CombiningAlgorithm;
import io.sapl.interpreter.combinators.CombiningAlgorithmFactory;
import io.sapl.pdp.config.PolicyDecisionPointConfiguration;
import io.sapl.pdp.config.VariablesAndCombinatorSource;
import io.sapl.test.utils.ClasspathHelper;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;

@Slf4j
public class ClasspathVariablesAndCombinatorSource implements VariablesAndCombinatorSource {
	
	private static final String DEFAULT_CONFIG_PATH = "/policies";
	private static final String CONFIG_FILE_GLOB_PATTERN = "pdp.json";

	private final PolicyDecisionPointConfiguration config;
	
	
	public ClasspathVariablesAndCombinatorSource() {
		this(DEFAULT_CONFIG_PATH);
	}

	public ClasspathVariablesAndCombinatorSource(String configPath) {
		this(configPath, new ObjectMapper());
	}

	public ClasspathVariablesAndCombinatorSource(@NonNull String configPath,
			@NonNull ObjectMapper mapper) {
		log.info("Loading the PDP configuration from bundled resources: '{}'", configPath);
		
		Path configDirectoryPath = ClasspathHelper.findPathOnClasspath(getClass(), configPath);
		
		log.debug("reading config from directory {}", configDirectoryPath);
		PolicyDecisionPointConfiguration pdpConfig = null;
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(configDirectoryPath, CONFIG_FILE_GLOB_PATTERN)) {
			for (Path filePath : stream) {
				log.info("loading PDP configuration: {}", filePath.toAbsolutePath());
				pdpConfig = mapper.readValue(filePath.toFile(), PolicyDecisionPointConfiguration.class);
				break;
			}
		} catch (IOException e) {
			throw Exceptions.propagate(e);
		}
		if(pdpConfig == null) {
			log.info("No PDP configuration found in resources. Using defaults.");
			this.config = new PolicyDecisionPointConfiguration();
		} else {
			this.config = pdpConfig;
		}
	}
	
	
	@Override
	public Flux<Optional<CombiningAlgorithm>> getCombiningAlgorithm() {
		return Flux.just(config.getAlgorithm()).map(CombiningAlgorithmFactory::getCombiningAlgorithm).map(Optional::of);
	}

	@Override
	public Flux<Optional<Map<String, JsonNode>>> getVariables() {
		return Flux.just(config.getVariables()).map(HashMap::new).map(Optional::of);
	}

	@Override
	public void dispose() {
		// NOP nothing to dispose
	}
}

package io.sapl.spring.pdp.embedded;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.extension.jwt.JWTFunctionLibrary;
import io.sapl.extension.jwt.JWTLibraryService;
import io.sapl.extension.jwt.JWTPolicyInformationPoint;

@Configuration
@ConditionalOnClass(name = "io.sapl.extension.jwt.JWTLibraryService")
public class JwtExtensionAutoConfiguration {

	@Bean
	public JWTLibraryService jwtLibraryService(ObjectMapper mapper) {
		return new JWTLibraryService(mapper);
	}

	@Bean
	public JWTFunctionLibrary jwtFunctionLibrary(ObjectMapper mapper) {
		return new JWTFunctionLibrary(mapper);
	}

	@Bean
	public JWTPolicyInformationPoint jwtPolicyInformationPoint(JWTLibraryService jwtService, ObjectMapper mapper,
			WebClient.Builder builder) {
		return new JWTPolicyInformationPoint(jwtService, builder);
	}

}

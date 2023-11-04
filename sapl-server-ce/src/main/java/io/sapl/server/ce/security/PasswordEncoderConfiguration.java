package io.sapl.server.ce.security;

import java.security.SecureRandom;
import java.util.HashMap;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder.BCryptVersion;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordEncoderConfiguration {
	@Bean
	public PasswordEncoder passwordEncoder() {
		var encodingId = "bcrypt";
		var encoders = new HashMap<String, PasswordEncoder>();
		encoders.put(encodingId, new BCryptPasswordEncoder(BCryptVersion.$2B, 14, new SecureRandom()));
		return new DelegatingPasswordEncoder(encodingId, encoders);
	}
}

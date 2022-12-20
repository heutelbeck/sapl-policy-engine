package io.sapl.spring.pdp.embedded;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.pdp.interceptors.ReportingDecisionInterceptor;
import lombok.RequiredArgsConstructor;

@AutoConfiguration
@RequiredArgsConstructor
public class InterceptorAutoConfiguration {

	private final ObjectMapper          mapper;
	private final EmbeddedPDPProperties properties;

	@Bean
	@ConditionalOnMissingBean
	ReportingDecisionInterceptor reportingDecisionInterceptor() {
		return new ReportingDecisionInterceptor(mapper, properties.isPrettyPrintReports(), properties.isPrintTrace(),
				properties.isPrintJsonReport(), properties.isPrintTextReport());
	}

}

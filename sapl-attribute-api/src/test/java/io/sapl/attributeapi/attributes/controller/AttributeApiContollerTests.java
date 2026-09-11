package io.sapl.attributeapi.attributes.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.github.dockerjava.zerodep.shaded.org.apache.hc.core5.http.HttpHeaders;

import io.lettuce.core.RedisConnectionException;
import io.sapl.attributeapi.attributes.backend.AttributeBackendUnavailableException;
import io.sapl.attributeapi.attributes.service.AttributeApiService;

@WebMvcTest(AttributeApiController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "io.sapl.attribute-api.enabled=true")
class AttributeApiContollerTests {
	@Autowired
	private MockMvc mockMvc;
	
	@MockitoBean
	private AttributeApiService service;
	
	@Test
	@DisplayName("When the backend is missing then a GET will return a 503 - service unavailble with a generic message")
	void whenBackendUnavailableThenHttpClientReceivesGenericmessage() throws Exception {
		var interrupted = new RedisConnectionException("Connection refused to redis");
		when(service.get(isNull(), eq("sapl.test"), any(), any()))
		.thenThrow(new AttributeBackendUnavailableException("The service is currently unavailable", interrupted));
		
		MvcResult result = mockMvc.perform(get("/api/attributes/sapl.test"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(header().string(HttpHeaders.RETRY_AFTER, "5"))
				.andReturn();
		
		String body = result.getResponse().getContentAsString();
		assertThat(body).isEqualTo("The service is currently unavailable").doesNotContain("Redis", "Connection refused", "RedisConnectionException");
	}
}

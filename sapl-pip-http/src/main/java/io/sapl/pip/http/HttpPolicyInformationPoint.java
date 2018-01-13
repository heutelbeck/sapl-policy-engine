package io.sapl.pip.http;

import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.pip.Attribute;
import io.sapl.api.pip.AttributeException;
import io.sapl.api.pip.PolicyInformationPoint;
import io.sapl.api.validation.JsonObject;
import io.sapl.api.validation.Text;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@PolicyInformationPoint(name = HttpPolicyInformationPoint.NAME, description = HttpPolicyInformationPoint.DESCRIPTION)
public class HttpPolicyInformationPoint {
	public static final String NAME = "http";
	public static final String DESCRIPTION = "Policy Information Point and attributes for consuming HTTP services";

	private static final String OBJECT_NO_HTTP_REQUEST_OBJECT_SPECIFICATION = "Object no HTTP request object specification.";

	@Attribute
	public JsonNode get(@Text @JsonObject JsonNode value, Map<String, JsonNode> variables) throws AttributeException {
		RequestSpecification saplRequest = new RequestSpecification();
		if (value.isTextual()) {
			saplRequest.setUrl(value);
		} else {
			try {
				saplRequest = RequestSpecification.from(value);
			} catch (JsonProcessingException e) {
				throw new AttributeException(OBJECT_NO_HTTP_REQUEST_OBJECT_SPECIFICATION, e);
			}
		}
		return RequestExecutor.executeUriRequest(saplRequest, RequestSpecification.HTTP_GET);
	}

	@Attribute
	public JsonNode post(@Text @JsonObject JsonNode value, Map<String, JsonNode> variables) throws AttributeException {
		RequestSpecification saplRequest = new RequestSpecification();
		if (value.isTextual()) {
			saplRequest.setUrl(value);
		} else {
			try {
				saplRequest = RequestSpecification.from(value);
			} catch (JsonProcessingException e) {
				throw new AttributeException(OBJECT_NO_HTTP_REQUEST_OBJECT_SPECIFICATION, e);
			}
		}
		return RequestExecutor.executeUriRequest(saplRequest, RequestSpecification.HTTP_POST);
	}

	@Attribute
	public JsonNode put(@Text @JsonObject JsonNode value, Map<String, JsonNode> variables) throws AttributeException {
		RequestSpecification saplRequest = new RequestSpecification();
		if (value.isTextual()) {
			saplRequest.setUrl(value);
		} else {
			try {
				saplRequest = RequestSpecification.from(value);
			} catch (JsonProcessingException e) {
				throw new AttributeException(OBJECT_NO_HTTP_REQUEST_OBJECT_SPECIFICATION, e);
			}
		}
		return RequestExecutor.executeUriRequest(saplRequest, RequestSpecification.HTTP_PUT);
	}

	@Attribute
	public JsonNode patch(@Text @JsonObject JsonNode value, Map<String, JsonNode> variables) throws AttributeException {
		RequestSpecification saplRequest = new RequestSpecification();
		if (value.isTextual()) {
			saplRequest.setUrl(value);
		} else {
			try {
				saplRequest = RequestSpecification.from(value);
			} catch (JsonProcessingException e) {
				throw new AttributeException(OBJECT_NO_HTTP_REQUEST_OBJECT_SPECIFICATION, e);
			}
		}
		return RequestExecutor.executeUriRequest(saplRequest, RequestSpecification.HTTP_PATCH);
	}

	@Attribute
	public JsonNode delete(@Text @JsonObject JsonNode value, Map<String, JsonNode> variables)
			throws AttributeException {
		RequestSpecification saplRequest = new RequestSpecification();
		if (value.isTextual()) {
			saplRequest.setUrl(value);
		} else {
			try {
				saplRequest = RequestSpecification.from(value);
			} catch (JsonProcessingException e) {
				throw new AttributeException(OBJECT_NO_HTTP_REQUEST_OBJECT_SPECIFICATION, e);
			}
		}
		return RequestExecutor.executeUriRequest(saplRequest, RequestSpecification.HTTP_DELETE);
	}
}

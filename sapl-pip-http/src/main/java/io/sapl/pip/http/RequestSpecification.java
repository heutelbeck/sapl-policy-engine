package io.sapl.pip.http;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.StringEntity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.pip.AttributeException;
import lombok.Data;

@Data
public class RequestSpecification {

	public static final String HTTP_GET = "get";
	public static final String HTTP_POST = "post";
	public static final String HTTP_PUT = "put";
	public static final String HTTP_PATCH = "patch";
	public static final String HTTP_DELETE = "delete";
	private static final String INVALID_HTTP_ACTION = "Requested action is invalid.";
	private static final String BAD_URL_INFORMATION = "Bad URL information.";
	private static final String NO_URL_PROVIDED = "No URL provided.";
	private static final String JSON_BODY_PROCESSING_ERROR = "JSON body processing error.";

	private static final ObjectMapper MAPPER = new ObjectMapper();

	JsonNode url;
	Map<String, String> header;
	String rawBody;
	JsonNode body;

	public static final RequestSpecification from(JsonNode value) throws JsonProcessingException {
		return MAPPER.treeToValue(value, RequestSpecification.class);
	}

	public HttpUriRequest toHttpUriRequest(String requestType) throws AttributeException {
		String requestUrl = buildUrl();
		HttpUriRequest request;

		if (HTTP_GET.equals(requestType)) {
			request = new HttpGet(requestUrl);
		} else if (HTTP_POST.equals(requestType)) {
			request = new HttpPost(requestUrl);
			HttpEntity entity = getEntity();
			if (entity != null) {
				((HttpPost) request).setEntity(entity);
			}
		} else if (HTTP_PUT.equals(requestType)) {
			request = new HttpPut(requestUrl);
			HttpEntity entity = getEntity();

			if (entity != null) {
				((HttpPut) request).setEntity(entity);
			}
		} else if (HTTP_PATCH.equals(requestType)) {
			request = new HttpPatch(requestUrl);
			HttpEntity entity = getEntity();
			if (entity != null) {
				((HttpPatch) request).setEntity(entity);
			}
		} else if (HTTP_DELETE.equals(requestType)) {
			request = new HttpDelete(requestUrl);
		} else {
			throw new AttributeException(INVALID_HTTP_ACTION);
		}

		setHeaders(request, getHeader());
		return request;
	}

	private static void setHeaders(HttpUriRequest request, Map<String, String> header) {
		if (header != null) {
			for (Entry<String, String> entry : header.entrySet()) {
				request.addHeader(entry.getKey(), entry.getValue());
			}
		}
	}

	private String buildUrl() throws AttributeException {
		String result;
		if (getUrl() == null) {
			throw new AttributeException(NO_URL_PROVIDED);
		} else if (getUrl().isTextual()) {
			result = getUrl().asText();
		} else if (getUrl().isObject()) {
			try {
				URLSpecification urlSpec = MAPPER.treeToValue(getUrl(), URLSpecification.class);
				result = urlSpec.toString();
			} catch (JsonProcessingException e) {
				throw new AttributeException(BAD_URL_INFORMATION, e);
			}
		} else {
			throw new AttributeException(BAD_URL_INFORMATION);
		}
		return result;
	}

	private HttpEntity getEntity() throws AttributeException {
		if (getBody() != null) {
			try {
				return new StringEntity(MAPPER.writeValueAsString(getBody()));
			} catch (UnsupportedEncodingException | JsonProcessingException e) {
				throw new AttributeException(JSON_BODY_PROCESSING_ERROR, e);
			}
		} else if (getRawBody() != null) {
			return new ByteArrayEntity(getRawBody().getBytes(StandardCharsets.UTF_8));
		} else {
			return null;
		}
	}

}

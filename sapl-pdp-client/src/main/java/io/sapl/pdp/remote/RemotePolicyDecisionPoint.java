package io.sapl.pdp.remote;

import static io.sapl.webclient.URLSpecification.HTTPS_SCHEME;
import static org.springframework.http.HttpMethod.POST;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.api.pdp.Request;
import io.sapl.api.pdp.Response;
import io.sapl.api.pdp.multirequest.IdentifiableResponse;
import io.sapl.api.pdp.multirequest.MultiRequest;
import io.sapl.webclient.RequestSpecification;
import io.sapl.webclient.WebClientRequestExecutor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
public class RemotePolicyDecisionPoint implements PolicyDecisionPoint {

	public static final String APPLICATION_JSON_VALUE = "application/json;charset=UTF-8";
	public static final String AUTHORIZATION_REQUEST = "/api/authorizationRequests";

	private static final String PDP_PATH = "/api/pdp/decide";

	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	private HttpHost httpHost;
	private HttpClientContext clientContext;

	private String hostName;
	private int port;

	public RemotePolicyDecisionPoint(String hostName, int port, String applicationKey, String applicationSecret) {
		this(hostName, port);

		httpHost = new HttpHost(hostName, port, HTTPS_SCHEME);
		CredentialsProvider credsProvider = new BasicCredentialsProvider();
		credsProvider.setCredentials(new AuthScope(httpHost.getHostName(), httpHost.getPort()),
				new UsernamePasswordCredentials(applicationKey, applicationSecret));

		AuthCache authCache = new BasicAuthCache();
		BasicScheme basicAuth = new BasicScheme();
		authCache.put(httpHost, basicAuth);

		clientContext = HttpClientContext.create();
		clientContext.setCredentialsProvider(credsProvider);
		clientContext.setAuthCache(authCache);
	}

	public RemotePolicyDecisionPoint(String hostName, int port) {
		this.hostName = hostName;
		this.port = port;

		MAPPER.registerModule(new Jdk8Module());
	}

	@Override
	public Flux<Response> decide(Object subject, Object action, Object resource) {
		return decide(subject, action, resource, null);
	}

	@Override
	public Flux<Response> decide(Object subject, Object action, Object resource, Object environment) {
		final Request request = toRequest(subject, action, resource, environment);
		return decide(request);
	}

	@Override
	public Flux<Response> decide(Request request) {
		final RequestSpecification saplRequest = getRequestSpecification(request);
		return new WebClientRequestExecutor().executeReactiveRequest(saplRequest, POST)
				.map(jsonNode -> MAPPER.convertValue(jsonNode, Response.class));
	}

	@Override
	public Flux<IdentifiableResponse> decide(MultiRequest multiRequest) {
		return null;
	}

	private static Request toRequest(Object subject, Object action, Object resource, Object environment) {
		return new Request(
				MAPPER.convertValue(subject, JsonNode.class),
				MAPPER.convertValue(action, JsonNode.class),
				MAPPER.convertValue(resource, JsonNode.class),
				MAPPER.convertValue(environment, JsonNode.class)
		);
	}

	private Response blockingDecide(Request request) {
		HttpPost post = new HttpPost(AUTHORIZATION_REQUEST);
		post.addHeader("content-type", APPLICATION_JSON_VALUE);
		try {
			String body = MAPPER.writeValueAsString(request);
			HttpEntity entity = new ByteArrayEntity(body.getBytes(StandardCharsets.UTF_8));
			post.setEntity(entity);
			return executeHttpRequest(post);
		} catch (JsonProcessingException e) {
			LOGGER.error("Marshalling request failed: {}", request, e);
			return new Response(Decision.INDETERMINATE, null, null, null);
		} catch (IOException e) {
			LOGGER.error("Request failed: {}", post, e);
			return new Response(Decision.INDETERMINATE, null, null, null);
		}
	}

	private Response executeHttpRequest(HttpRequest request) throws IOException {
		Response response = null;
		try (CloseableHttpClient httpClient = HttpClients.createDefault();
			 CloseableHttpResponse webResponse = httpClient.execute(httpHost, request, clientContext)) {

			int resultCode = webResponse.getStatusLine().getStatusCode();
			if (resultCode != HttpStatus.SC_OK) {
				throw new IOException("Error " + resultCode + ": " + webResponse.getStatusLine().getReasonPhrase());
			}

			HttpEntity responseEntity = webResponse.getEntity();
			if (responseEntity != null) {
				String responseEntityText = EntityUtils.toString(responseEntity);
				if (!responseEntityText.isEmpty()) {
					/*
					 * JsonNode result = MAPPER.readValue(responseEntityText, JsonNode.class);
					 * response = new Response( Decision.valueOf(result.get("decision").asText()),
					 * result.get("resource"), result.has("obligation") ? (ArrayNode)
					 * result.get("obligation") : null, result.has("advice") ? (ArrayNode)
					 * result.get("advice") : null);
					 */
					response = MAPPER.readValue(responseEntityText, Response.class);
				}
				EntityUtils.consume(responseEntity);
			}
		}
		return response;
	}

	private RequestSpecification getRequestSpecification(Request request) {
		final String url = HTTPS_SCHEME + "://" + hostName + ":" + port + PDP_PATH;
		final TextNode urlNode = JSON.textNode(url);
		final JsonNode bodyNode = MAPPER.convertValue(request, JsonNode.class);
		final RequestSpecification saplRequest = new RequestSpecification();
		saplRequest.setUrl(urlNode);
		saplRequest.setBody(bodyNode);
		return saplRequest;
	}

	@Override
	public void dispose() {
		// ignored
	}
}

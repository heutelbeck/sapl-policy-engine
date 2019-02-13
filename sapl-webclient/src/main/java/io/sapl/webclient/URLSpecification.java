package io.sapl.webclient;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class URLSpecification {

	public static final String HTTP_SCHEME = "http";
	public static final String HTTPS_SCHEME = "https";

    private String scheme;
	private String user;
	private String password;
	private String host;
	private Integer port;
	private String path;
	private String rawQuery;
	private Map<String, String> queryParameters;
	private String fragment;

	static URLSpecification from(String urlStr) throws MalformedURLException {
		final URL url = new URL(urlStr);
		return new URLSpecification(url.getProtocol(), getUser(url), getPassword(url), url.getHost(), getPort(url), url.getPath(), url.getQuery(), getQueryParameters(url), url.getRef());
	}

	private static String getUser(URL url) {
		final String userInfo = url.getUserInfo();
		if (userInfo != null) {
			final String[] userPassword = userInfo.split(":");
			if (userPassword.length > 0) {
				return userPassword[0];
			}
		}
		return null;
	}

	private static String getPassword(URL url) {
		final String userInfo = url.getUserInfo();
		if (userInfo != null) {
			final String[] userPassword = userInfo.split(":");
			if (userPassword.length > 1) {
				return userPassword[1];
			}
		}
		return null;
	}

	private static Integer getPort(URL url) {
		return url.getPort() > 0 ? url.getPort() : null;
	}

	private static Map<String, String> getQueryParameters(URL url) {
		final String query = url.getQuery();
		if (query != null) {
			final Map<String, String> queryParams = new HashMap<>();
			final String[] nameValuePairs = query.split("&");
			for (String nameValuePair : nameValuePairs) {
				final String[] nameValue = nameValuePair.split("=");
				queryParams.put(nameValue[0].startsWith("?") ? nameValue[0].substring(1) : nameValue[0], nameValue.length > 1 ? nameValue[1] : "");
			}
			return queryParams;
		}
		return null;
	}

	String asString() {
		return baseUrl() + pathAndQueryString() + fragment();
	}

	String baseUrl() {
		final StringBuilder sb = new StringBuilder();
		if (scheme != null) {
			sb.append(scheme).append(':');
		}
		if (host != null) {
			sb.append("//");
			if (user != null) {
				sb.append(user);
				if (password != null) {
					sb.append(':').append(password);
				}
				sb.append('@');
			}
			sb.append(host);
			if (port != null) {
				sb.append(':').append(port);
			}
		}
		return sb.toString();
	}

	String pathAndQueryString() {
		final StringBuilder sb = new StringBuilder();
		if (path != null) {
			if (!path.startsWith("/")) {
				sb.append('/');
			}
			sb.append(path);
		}
		if (rawQuery != null) {
			if (!rawQuery.startsWith("?")) {
				sb.append('?');
			}
			sb.append(rawQuery);
		} else if (queryParameters != null) {
			sb.append('?');
			boolean first = true;
			for (Entry<String, String> entry : queryParameters.entrySet()) {
			    if (! first) {
			        sb.append('&');
                }
				sb.append(entry.getKey()).append('=').append(entry.getValue());
				first = false;
			}
		}
		return sb.toString();
	}

	String fragment() {
		final StringBuilder sb = new StringBuilder();
		if (fragment != null) {
			if (!fragment.startsWith("#")) {
				sb.append('#');
			}
			sb.append(fragment);
		}
		return sb.toString();
	}
}

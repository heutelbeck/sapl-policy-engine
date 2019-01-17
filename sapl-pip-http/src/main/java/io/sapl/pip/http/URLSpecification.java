package io.sapl.pip.http;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map.Entry;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class URLSpecification {

	private static final String HASH = "#";
	private static final String AMPERSAND = "&";
	private static final char EQUALS = '=';
	private static final String QUESTIONMARK = "?";
	private static final String SLASH = "/";
	private static final String DOUBLE_SLASH = "//";
	private static final char COLON = ':';

	private String scheme;
	private String user;
	private String password;
	private String host;
	private Integer port;
	private String path;
	private String rawQuery;
	private HashMap<String, String> queryParameters;
	private String fragment;

	static URLSpecification from(String urlStr) throws MalformedURLException {
		final URL url = new URL(urlStr);
		return new URLSpecification(url.getProtocol(), getUser(url), getPassword(url), url.getHost(), url.getPort(), url.getPath(), url.getQuery(), null, url.getRef());
	}

	private static String getUser(URL url) {
		final String userInfo = url.getUserInfo();
		if (userInfo != null) {
			final String[] userPassword = userInfo.split(":");
			if (userPassword.length == 2) {
				return userPassword[0];
			}
		}
		return null;
	}

	private static String getPassword(URL url) {
		final String userInfo = url.getUserInfo();
		if (userInfo != null) {
			final String[] userPassword = userInfo.split(":");
			if (userPassword.length == 2) {
				return userPassword[1];
			}
		}
		return null;
	}

	String asString() {
		return baseUrl() + pathAndQueryString() + fragment();
	}

	String baseUrl() {
		final StringBuilder sb = new StringBuilder();
		if (scheme != null) {
			sb.append(scheme).append(COLON);
		}
		if (host != null) {
			sb.append(DOUBLE_SLASH);
			if (user != null) {
				sb.append(user);
				if (password != null) {
					sb.append(COLON).append(password);
				}
			}
			if (port != null) {
				sb.append(COLON).append(port);
			}
		}
		return sb.toString();
	}

	String pathAndQueryString() {
		final StringBuilder sb = new StringBuilder();
		if (path != null) {
			if (!path.startsWith(SLASH)) {
				sb.append(SLASH);
			}
			sb.append(path);
		}
		if (rawQuery != null) {
			if (!rawQuery.startsWith(QUESTIONMARK)) {
				sb.append(QUESTIONMARK);
			}
			sb.append(rawQuery);
		} else if (queryParameters != null) {
			sb.append(QUESTIONMARK);
			String delim = "";
			for (Entry<String, String> entry : queryParameters.entrySet()) {
				sb.append(entry.getKey()).append(EQUALS).append(entry.getValue()).append(delim);
				delim = AMPERSAND;
			}
		}
		return sb.toString();
	}

	String fragment() {
		final StringBuilder sb = new StringBuilder();
		if (fragment != null) {
			if (!fragment.startsWith(HASH)) {
				sb.append(HASH);
			}
			sb.append(fragment);
		}
		return sb.toString();
	}
}

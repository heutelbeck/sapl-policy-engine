package io.sapl.springboot.autoconfig;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "pdp")
public class PDPProperties {

	public enum PDPType {
		EMBEDDED, REMOTE;
	}

	private PDPType type = PDPType.EMBEDDED;

	private Remote remote = new Remote();

	private Embedded embedded = new Embedded();

	private boolean policyEnforcementFilter;

	private ObligationHandler obligationsHandler = new ObligationHandler();

	@Getter
	@Setter
	public static class Embedded {
		private boolean active = true;
		private String policyPath = "~/policies";

	}

	@Getter
	@Setter
	public static class Remote {
		private static final int DEFAULT_REMOTE_PORT = 8443;
		private boolean active;
		// private InetAddress remoteAddress;
		private String host = "localhost";
		private int port = DEFAULT_REMOTE_PORT;
		private String key;
		private String secret;
	}

	@Getter
	@Setter
	public static class ObligationHandler {

		private boolean autoregister = true;
	}
}

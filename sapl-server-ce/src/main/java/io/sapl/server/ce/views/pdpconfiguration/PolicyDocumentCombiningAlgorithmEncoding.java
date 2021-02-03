package io.sapl.server.ce.views.pdpconfiguration;

import java.util.Map;
import java.util.stream.Stream;

import com.google.common.collect.Maps;

import io.sapl.api.pdp.PolicyDocumentCombiningAlgorithm;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Utils for encoding a {@link PolicyDocumentCombiningAlgorithm} to a
 * {@link String} for the UI.
 */
@Slf4j
@UtilityClass
class PolicyDocumentCombiningAlgorithmEncoding {
	private static final Map<PolicyDocumentCombiningAlgorithm, String> mapping = generateMapping();

	public static String encode(@NonNull PolicyDocumentCombiningAlgorithm entry) {
		return mapping.get(entry);
	}

	public static String[] encode(@NonNull PolicyDocumentCombiningAlgorithm[] policyDocumentCombiningAlgorithm) {
		//@formatter:off
		return Stream.of(policyDocumentCombiningAlgorithm)
				.map(entry -> encode(entry))
				.toArray(String[]::new);
		//@formatter:on
	}

	public static PolicyDocumentCombiningAlgorithm decode(@NonNull String encodedEntry) {
		for (PolicyDocumentCombiningAlgorithm entry : mapping.keySet()) {
			String currentEncodedEntry = mapping.get(entry);

			if (currentEncodedEntry.equals(encodedEntry)) {
				return entry;
			}
		}

		throw new IllegalArgumentException(String.format("no entry found for encoded entry: %s", encodedEntry));
	}

	private static Map<PolicyDocumentCombiningAlgorithm, String> generateMapping() {
		Map<PolicyDocumentCombiningAlgorithm, String> mapping = Maps.newHashMap();

		for (PolicyDocumentCombiningAlgorithm entry : PolicyDocumentCombiningAlgorithm.values()) {
			String encoded;
			switch (entry) {
			case DENY_UNLESS_PERMIT:
				encoded = "deny-unless-permit";
				break;
			case PERMIT_UNLESS_DENY:
				encoded = "permit-unless-deny";
				break;
			case ONLY_ONE_APPLICABLE:
				encoded = "only-one-applicable";
				break;
			case DENY_OVERRIDES:
				encoded = "deny-overrides";
				break;
			case PERMIT_OVERRIDES:
				encoded = "permit-overrides";
				break;

			default:
				encoded = entry.toString();
				log.warn("cannot encode entry of {}: {}", PolicyDocumentCombiningAlgorithm.class.toString(), encoded);
				break;
			}

			mapping.put(entry, encoded);
		}

		return mapping;
	}
}

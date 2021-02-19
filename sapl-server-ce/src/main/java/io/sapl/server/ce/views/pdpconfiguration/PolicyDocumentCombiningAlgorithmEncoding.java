package io.sapl.server.ce.views.pdpconfiguration;

import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Stream;

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
		for (Map.Entry<PolicyDocumentCombiningAlgorithm, String> entry : mapping.entrySet()) {
			String currentEncodedEntry = entry.getValue();

			if (currentEncodedEntry.equals(encodedEntry)) {
				return entry.getKey();
			}
		}

		throw new IllegalArgumentException(String.format("no entry found for encoded entry: %s", encodedEntry));
	}

	private static Map<PolicyDocumentCombiningAlgorithm, String> generateMapping() {
		PolicyDocumentCombiningAlgorithm[] combiningAlgorithms = PolicyDocumentCombiningAlgorithm.values();

		EnumMap<PolicyDocumentCombiningAlgorithm, String> mapping = new EnumMap<>(
				PolicyDocumentCombiningAlgorithm.class);
		for (PolicyDocumentCombiningAlgorithm combiningAlgorithm : combiningAlgorithms) {
			String encoded;
			switch (combiningAlgorithm) {
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
				encoded = combiningAlgorithm.toString();
				log.warn("cannot encode entry of {}: {}", PolicyDocumentCombiningAlgorithm.class, encoded);
				break;
			}

			mapping.put(combiningAlgorithm, encoded);
		}

		return mapping;
	}
}

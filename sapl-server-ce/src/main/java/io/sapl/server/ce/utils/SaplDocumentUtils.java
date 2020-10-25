package io.sapl.server.ce.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.sapl.server.ce.model.sapldocument.SaplDocument;
import io.sapl.server.ce.model.sapldocument.SaplDocumentType;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

/**
 * Utils for handling of {@link SaplDocument}s.
 */
@UtilityClass
public final class SaplDocumentUtils {
	private static final String POLICY_SET_NAME_DESCRIPTION = "set";
	private static final String POLICY_NAME_DESCRIPTION = "policy";

	public SaplDocumentType getType(@NonNull String documentValue) {
		Pattern pattern = Pattern.compile(String.format("%s \\\"(.*)+\\\"", POLICY_SET_NAME_DESCRIPTION));
		Matcher matcher = pattern.matcher(documentValue);
		if (matcher.find()) {
			return SaplDocumentType.POLICY_SET;
		} else {
			return SaplDocumentType.POLICY;
		}
	}

	/**
	 * Gets the referenced name of a document value of a {@link SaplDocument}. The
	 * document value can be a policy set or a single policy.
	 * 
	 * @param documentValue the document value
	 * @return the referenced name in the provided document value
	 * @exception IllegalArgumentException thrown if the document value is malformed
	 */
	public String getNameFromDocumentValue(@NonNull String documentValue) {
		SaplDocumentType type = SaplDocumentUtils.getType(documentValue);

		String nameDescription;
		switch (type) {
		case POLICY:
			nameDescription = POLICY_NAME_DESCRIPTION;
			break;
		case POLICY_SET:
			nameDescription = POLICY_SET_NAME_DESCRIPTION;
			break;
		default:
			throw new IllegalStateException(String.format("the type %s is not supported", type));
		}

		Pattern pattern = Pattern.compile(String.format("%s \\\"(.*)+\\\"", nameDescription));
		Matcher matcher = pattern.matcher(documentValue);
		if (!matcher.find()) {
			throw new IllegalArgumentException(
					String.format("document value does not contain a name (%s)", documentValue));
		}

		String match = matcher.group(0);
		return match.substring(match.indexOf('\"') + 1, match.lastIndexOf('\"'));
	}
}

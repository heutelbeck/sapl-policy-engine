package io.sapl.pdpserver.utils.test;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import io.sapl.server.ce.model.sapldocument.SaplDocumentType;
import io.sapl.server.ce.utils.SaplDocumentUtils;

public class SaplDocumentUtilsTest {
	@Test
	public void testGetType() {
		assertEquals(SaplDocumentType.POLICY_SET, SaplDocumentUtils.getType("import filter.* \r\n" + "\r\n"
				+ "set \"test_policy_set\" \r\n" + "deny-unless-permit \r\n" + "for resource.type == \"aType\" \r\n"
				+ "var dbUser = \"admin\";\r\n" + "\r\n" + "    policy \"test_permit_admin\" \r\n"
				+ "    permit subject.function == \"admin\"\r\n" + "\r\n" + "    policy \"test_permit_read\" \r\n"
				+ "    permit action == \"read\"\r\n" + "    transform resource |- blacken"));

		assertEquals(SaplDocumentType.POLICY, SaplDocumentUtils.getType("policy \"policyName\"\r\n" + "permit"));
	}

	@Test
	public void testGetNameFromDocumentValue() {
		assertEquals("myPolicyName",
				SaplDocumentUtils.getNameFromDocumentValue("policy \"myPolicyName\"\r\n" + "permit"));

		assertEquals("test_policy_set", SaplDocumentUtils.getNameFromDocumentValue("import filter.* \r\n" + "\r\n"
				+ "set \"test_policy_set\" \r\n" + "deny-unless-permit \r\n" + "for resource.type == \"aType\" \r\n"
				+ "var dbUser = \"admin\";\r\n" + "\r\n" + "    policy \"test_permit_admin\" \r\n"
				+ "    permit subject.function == \"admin\"\r\n" + "\r\n" + "    policy \"test_permit_read\" \r\n"
				+ "    permit action == \"read\"\r\n" + "    transform resource |- blacken"));

		// name with spaces
		assertEquals("all deny", SaplDocumentUtils.getNameFromDocumentValue("policy \"all deny\"\r\ndeny"));
	}
}

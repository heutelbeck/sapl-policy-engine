/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.interpreter

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ArrayNode
import io.sapl.api.pdp.AuthorizationDecision
import io.sapl.api.pdp.AuthorizationSubscription
import io.sapl.api.pdp.Decision
import io.sapl.functions.FilterFunctionLibrary
import io.sapl.interpreter.functions.AnnotationFunctionContext
import io.sapl.interpreter.pip.AnnotationAttributeContext
import java.util.Collections
import java.util.HashMap
import java.util.Map
import java.util.Optional
import lombok.extern.slf4j.Slf4j
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import static org.hamcrest.CoreMatchers.equalTo
import static org.hamcrest.MatcherAssert.assertThat

@Slf4j
class SampleXACMLTests {

	static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
	static final DefaultSAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter();
	static final AnnotationAttributeContext ATTRIBUTE_CTX = new AnnotationAttributeContext();
	static final AnnotationFunctionContext FUNCTION_CTX = new AnnotationFunctionContext();
	static final Map<String, JsonNode> SYSTEM_VARIABLES = Collections.unmodifiableMap(new HashMap<String, JsonNode>());

	static AuthorizationSubscription authzSubscription_example_two;

	@BeforeAll
	def static void setUpClass() {
		FUNCTION_CTX.loadLibrary(new MockXACMLStringFunctionLibrary());
		FUNCTION_CTX.loadLibrary(new MockXACMLDateFunctionLibrary());
		FUNCTION_CTX.loadLibrary(new FilterFunctionLibrary());
		ATTRIBUTE_CTX.loadPolicyInformationPoint(new MockXACMLPatientProfilePIP());
	}

	@BeforeEach
	def void setUp() {

		SampleXACMLTests.authzSubscription_example_two = MAPPER.readValue('''
			{
				"subject": {
					"id": "CN=Julius Hibbert",
					"role": "physician",
					"physician_id": "jh1234"
				},
				"resource": {
					"_type": "urn:example:med:schemas:record",
					"_content": {
						"patient": {
							"dob": "1992-03-21",
							"patient_number": "555555",
							"contact": {
								"email": "b.simpsons@example.com"
							}
						}
					},
					"_selector": "@.patient.dob"
				},
				"action": "read",
				"environment": {
					"current_date": "2010-01-11"
				}
			}
		''', AuthorizationSubscription)
	}

	def String policyExampleOne() {
		return '''
			policy "SimplePolicy1"
			/* Any subject with an e-mail name in the med.example.com 
			    domain can perform any action on any resource. */
			
			permit subject =~ "(?i).*@med\\.example\\.com"
		''';
	}

	@Test
	def void exampleOne() {
		val authzSubscription_object = MAPPER.readValue('''
			{
				"subject": "bs@simpsons.com",
				"resource": "file://example/med/record/patient/BartSimpson",
				"action": "read"
			}
		''', AuthorizationSubscription)

		val expectedAuthzDecision = AuthorizationDecision.NOT_APPLICABLE

		assertThat("XACML example one not working as expected",
			INTERPRETER.evaluate(authzSubscription_object, policyExampleOne(), ATTRIBUTE_CTX, FUNCTION_CTX,
				SYSTEM_VARIABLES).blockFirst(), equalTo(expectedAuthzDecision));
	}

	@Test
	def void exampleOnePermit() {
		val authzSubscription_object = MAPPER.readValue('''
			{
				"subject": "abc@Med.example.com",
				"resource": "file://example/med/record/patient/BartSimpson",
				"action": "read"
			}
		''', AuthorizationSubscription)

		val policyDefinition = '''
			policy "SimplePolicy1"
			/* Any subject with an e-mail name in the med.example.com 
			    domain can perform any action on any resource. */
			
			permit subject =~ "(?i).*@med\\.example\\.com"
		''';

		val expectedAuthzDecision = AuthorizationDecision.PERMIT

		assertThat("XACML example one not working as expected",
			INTERPRETER.evaluate(authzSubscription_object, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX,
				SYSTEM_VARIABLES).blockFirst(), equalTo(expectedAuthzDecision));
	}

	def String policyExampleTwoRule1() {
		return '''
			policy "rule_1"
			/* A person may read any medical record in the
			    http://www.med.example.com/schemas/record.xsd namespace
			    for which he or she is the designated patient */
			   
			permit 
				resource._type == "urn:example:med:schemas:record" &
				string.starts_with(resource._selector, "@") &
				action == "read"
			where
				subject.role == "patient";
				subject.patient_number == resource._content.patient.patient_number;
		''';
	}

	@Test
	def void exampleTwoRule1() {
		val expectedAuthzDecision = AuthorizationDecision.NOT_APPLICABLE

		assertThat("XACML example two rule 1 not working as expected",
			INTERPRETER.evaluate(authzSubscription_example_two, policyExampleTwoRule1(), ATTRIBUTE_CTX, FUNCTION_CTX,
				SYSTEM_VARIABLES).blockFirst(), equalTo(expectedAuthzDecision));
	}

	@Test
	def void exampleTwoRule1Permit() {
		val authzSubscription = MAPPER.readValue('''
			{
				"subject": {
					"id": "alice",
					"role": "patient",
					"patient_number": "555555"
				},
				"resource": {
					"_type": "urn:example:med:schemas:record",
					"_content": {
						"patient": {
							"dob": "1992-03-21",
							"patient_number": "555555",
							"contact": {
								"email": "b.simpsons@example.com"
							}
						}
					},
					"_selector": "@.patient.dob"
				},
				"action": "read",
				"environment": {
					"current_date": "2010-01-11"
				}
			}
		''', AuthorizationSubscription)

		val expectedAuthzDecision = AuthorizationDecision.PERMIT

		assertThat("XACML example two rule 1 not working as expected",
			INTERPRETER.evaluate(authzSubscription, policyExampleTwoRule1(), ATTRIBUTE_CTX, FUNCTION_CTX,
				SYSTEM_VARIABLES).blockFirst(), equalTo(expectedAuthzDecision));
	}

	def String policyExampleTwoRule2() {
		return '''
			policy "rule_2"
			/* A person may read any medical record in the
			    http://www.med.example.com/records.xsd namespace
			    for which he or she is the designated parent or guardian,
			    and for which the patient is under 16 years of age */
			   
			permit 
				resource._type == "urn:example:med:schemas:record" &
				string.starts_with(resource._selector, "@") &
				action == "read"
			where
				subject.role == "parent_guardian";
				subject.parent_guardian_id == resource._content.patient.patient_number.<patient.profile>.parentGuardian.id;
				date.diff("years", environment.current_date, resource._content.patient.dob) < 16;
		''';
	}

	@Test
	def void exampleTwoRule2() {
		val expectedAuthzDecision = AuthorizationDecision.NOT_APPLICABLE

		assertThat("XACML example two rule 2 not working as expected",
			INTERPRETER.evaluate(authzSubscription_example_two, policyExampleTwoRule2(), ATTRIBUTE_CTX, FUNCTION_CTX,
				SYSTEM_VARIABLES).blockFirst(), equalTo(expectedAuthzDecision));
	}

	@Test
	def void exampleTwoRule2Permit() {
		val authzSubscription = MAPPER.readValue('''
			{
				"subject": {
					"id": "john",
					"role": "parent_guardian",
					"parent_guardian_id": "HS001"
				},
				"resource": {
					"_type": "urn:example:med:schemas:record",
					"_content": {
						"patient": {
							"dob": "1992-03-21",
							"patient_number": "555555",
							"contact": {
								"email": "b.simpsons@example.com"
							}
						}
					},
					"_selector": "@.patient.dob"
				},
				"action": "read",
				"environment": {
					"current_date": "2010-01-11"
				}
			}
		''', AuthorizationSubscription)

		val expectedAuthzDecision = AuthorizationDecision.PERMIT

		assertThat("XACML example two rule 2 not working as expected",
			INTERPRETER.evaluate(authzSubscription, policyExampleTwoRule2(), ATTRIBUTE_CTX, FUNCTION_CTX,
				SYSTEM_VARIABLES).blockFirst(), equalTo(expectedAuthzDecision));
	}

	def String policyExampleTwoRule3() {
		return '''
			policy "rule_3"
			/* A physician may write any medical element in a record 
			    for which he or she is the designated primary care 
			    physician, provided an email is sent to the patient */
			
			permit 
				subject.role == "physician" &
				string.starts_with(resource._selector, "@.medical") &
				action == "write"
			where
				subject.physician_id == resource._content.primaryCarePhysician.registrationID;
			obligation
				{
					"id" : "email",
					"mailto" : resource._content.patient.contact.email,
					"text" : "Your medical record has been accessed by:" + subject.id
				}
		''';
	}

	@Test
	def void exampleTwoRule3() {
		val expectedAuthzDecision = AuthorizationDecision.NOT_APPLICABLE

		assertThat("XACML example two rule 3 not working as expected",
			INTERPRETER.evaluate(authzSubscription_example_two, policyExampleTwoRule3(), ATTRIBUTE_CTX, FUNCTION_CTX,
				SYSTEM_VARIABLES).blockFirst(), equalTo(expectedAuthzDecision));
	}

	@Test
	def void exampleTwoRule3Permit() {
		val authzSubscription = MAPPER.readValue('''
			{
				"subject": {
					"id": "CN=Julius Hibbert",
					"role": "physician",
					"physician_id": "jh1234"
				},
				"resource": {
					"_type": "urn:example:med:schemas:record",
					"_content": {
						"patient": {
							"dob": "1992-03-21",
							"patient_number": "555555",
							"contact": {
								"email": "b.simpsons@example.com"
							}
						},
						"primaryCarePhysician": {
							"registrationID": "jh1234"
						}
					},
					"_selector": "@.medical"
				},
				"action": "write",
				"environment": {
					"current_date": "2010-01-11"
				}
			}
		''', AuthorizationSubscription)

		val expectedObligation = MAPPER.readValue('''
			[
				{
					"id": "email",
					"mailto": "b.simpsons@example.com",
					"text": "Your medical record has been accessed by:CN=Julius Hibbert"
				}
			]
		''', ArrayNode)

		val expectedAuthzDecision = new AuthorizationDecision(Decision.PERMIT, Optional.empty,
			Optional.of(expectedObligation), Optional.empty)

		assertThat("XACML example two rule 3 not working as expected",
			INTERPRETER.evaluate(authzSubscription, policyExampleTwoRule3(), ATTRIBUTE_CTX, FUNCTION_CTX,
				SYSTEM_VARIABLES).blockFirst(), equalTo(expectedAuthzDecision));
	}

}

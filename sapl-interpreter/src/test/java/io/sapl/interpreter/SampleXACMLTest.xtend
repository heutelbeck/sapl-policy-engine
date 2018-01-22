package io.sapl.interpreter

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import io.sapl.api.interpreter.PolicyEvaluationException
import io.sapl.api.pdp.Decision
import io.sapl.api.pdp.Request
import io.sapl.api.pdp.Response
import io.sapl.interpreter.functions.AnnotationFunctionContext
import io.sapl.interpreter.functions.FunctionContext
import io.sapl.interpreter.pip.AnnotationAttributeContext
import io.sapl.interpreter.pip.AttributeContext
import java.util.Collections
import java.util.HashMap
import java.util.Map
import java.util.Optional
import org.junit.Before
import org.junit.Test

import static org.hamcrest.CoreMatchers.equalTo
import static org.junit.Assert.assertThat
import io.sapl.functions.SelectionFunctionLibrary

class SampleXACMLTest {

	private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
	private static final DefaultSAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter();
	private static final AttributeContext ATTRIBUTE_CTX = new AnnotationAttributeContext();
	private static final FunctionContext FUNCTION_CTX = new AnnotationFunctionContext();
	private static final Map<String, JsonNode> SYSTEM_VARIABLES = Collections.unmodifiableMap(
		new HashMap<String, JsonNode>());
		
	private static Request request_example_two;

	@Before
	def void init() {
		FUNCTION_CTX.loadLibrary(new MockXACMLStringFunctionLibrary());
		FUNCTION_CTX.loadLibrary(new MockXACMLDateFunctionLibrary());
		FUNCTION_CTX.loadLibrary(new SelectionFunctionLibrary());
		ATTRIBUTE_CTX.loadPolicyInformationPoint(new MockXACMLPatientProfilePIP());
		
		io.sapl.interpreter.SampleXACMLTest.request_example_two = MAPPER.readValue('''
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
		''', Request)
	}

	@Test
	def void exampleOne() throws PolicyEvaluationException {
		val request_object = MAPPER.readValue('''
			{
				"subject": "bs@simpsons.com",
				"resource": "file://example/med/record/patient/BartSimpson",
				"action": "read"
			}
		''', Request)

		val policyDefinition = '''
			policy "SimplePolicy1"
			/* Any subject with an e-mail name in the med.example.com 
			    domain can perform any action on any resource. */
			
			permit subject =~ "(?i).*@med\\.example\\.com"
		''';

		val expectedResponse = Response.notApplicable()

		assertThat("XACML example one not working as expected",
			INTERPRETER.evaluate(request_object, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX, SYSTEM_VARIABLES),
			equalTo(expectedResponse));
	}
	
	@Test
	def void exampleOnePermit() throws PolicyEvaluationException {
		val request_object = MAPPER.readValue('''
			{
				"subject": "abc@Med.example.com",
				"resource": "file://example/med/record/patient/BartSimpson",
				"action": "read"
			}
		''', Request)

		val policyDefinition = '''
			policy "SimplePolicy1"
			/* Any subject with an e-mail name in the med.example.com 
			    domain can perform any action on any resource. */
			
			permit subject =~ "(?i).*@med\\.example\\.com"
		''';

		val expectedResponse = new Response(Decision.PERMIT, Optional.empty(), Optional.empty(), Optional.empty());

		assertThat("XACML example one not working as expected",
			INTERPRETER.evaluate(request_object, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX, SYSTEM_VARIABLES),
			equalTo(expectedResponse));
	}
	
	@Test
	def void exampleTwoRule1() throws PolicyEvaluationException {
		val policyDefinition = '''
			policy "rule_1"
			/* A person may read any medical record in the
			    http://www.med.example.com/schemas/record.xsd namespace
			    for which he or she is the designated patient */
			   
			permit 
				resource._type == "urn:example:med:schemas:record" &&
				string.starts_with(resource._selector, "@") &&
				action == "read"
			where
				subject.role == "patient";
				subject.patient_number == resource._content.patient.patient_number;
		''';

		val expectedResponse = Response.notApplicable()

		assertThat("XACML example two rule 1 not working as expected",
			INTERPRETER.evaluate(io.sapl.interpreter.SampleXACMLTest.request_example_two, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX, SYSTEM_VARIABLES),
			equalTo(expectedResponse));
	}
	
	@Test
	def void exampleTwoRule2() throws PolicyEvaluationException {
		val policyDefinition = '''
			policy "rule_2"
			/* A person may read any medical record in the
			    http://www.med.example.com/records.xsd namespace
			    for which he or she is the designated parent or guardian,
			    and for which the patient is under 16 years of age */
			   
			permit 
				resource._type == "urn:example:med:schemas:record" &&
				string.starts_with(resource._selector, "@") &&
				action == "read"
			where
				subject.role == "parent_guardian";
				subject.parent_guardian_id == resource._content.patient.patient_number.<patient.profile>.parentGuardian.id;
				date.diff("years", environment.current_date, resource._content.patient.dob) < 16;
		''';

		val expectedResponse = Response.notApplicable()

		assertThat("XACML example two rule 2 not working as expected",
			INTERPRETER.evaluate(io.sapl.interpreter.SampleXACMLTest.request_example_two, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX, SYSTEM_VARIABLES),
			equalTo(expectedResponse));
	}
	
	@Test
	def void exampleTwoRule3() throws PolicyEvaluationException {
		val policyDefinition = '''
			policy "rule_3"
			/* A physician may write any medical element in a record 
			    for which he or she is the designated primary care 
			    physician, provided an email is sent to the patient */
			
			permit 
				subject.role == "physician" &&
				string.starts_with(resource._selector, "@.medical") &&
				action == "write"
			where
				subject.role == "physician";
				subject.physician_id == resource._content.primaryCarePhysician.registrationID;
			obligation
				{
					"id" : "email",
					"mailto" : resource._content.patient.contact.email,
					"text" : "Your medical record has been accessed by:" + subject.id
				}
		''';

		val expectedResponse = Response.notApplicable()

		assertThat("XACML example two rule 3 not working as expected",
			INTERPRETER.evaluate(io.sapl.interpreter.SampleXACMLTest.request_example_two, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX, SYSTEM_VARIABLES),
			equalTo(expectedResponse));
	}
	
	@Test
	def void exampleTwoRule4() throws PolicyEvaluationException {
		val policyDefinition = '''
			policy "rule_4"
			/* An Administrator shall not be permitted to read or write
			   medical elements of a patient record in the
			   http://www.med.example.com/records.xsd namespace. */
			
			deny
				subject.role == "administrator" &&
				resource._type == "urn:example:med:schemas:record" &&
				(action == "write" || action == "read") && 
				(
					selection.match(resource._content, resource._selector, "@.medical") ||
					selection.match(resource._content, "@.medical", resource._selector) 
				)
		''';

		val expectedResponse = Response.notApplicable()

		assertThat("XACML example two rule 4 not working as expected",
			INTERPRETER.evaluate(io.sapl.interpreter.SampleXACMLTest.request_example_two, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX, SYSTEM_VARIABLES),
			equalTo(expectedResponse));
	}
}

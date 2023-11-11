/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
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
import io.sapl.api.pdp.AuthorizationDecision
import io.sapl.api.pdp.AuthorizationSubscription
import io.sapl.api.pdp.Decision
import io.sapl.functions.FilterFunctionLibrary
import io.sapl.interpreter.functions.AnnotationFunctionContext
import io.sapl.interpreter.pip.AnnotationAttributeContext
import io.sapl.interpreter.pip.AttributeContext
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.Collections
import java.util.HashMap
import java.util.Map
import java.util.Optional
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import static org.hamcrest.CoreMatchers.equalTo
import static org.hamcrest.MatcherAssert.assertThat

class SampleOurPuppetTests {

	static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
	static final DefaultSAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter();
	static final AttributeContext ATTRIBUTE_CTX = new AnnotationAttributeContext();
	static final AnnotationFunctionContext FUNCTION_CTX = new AnnotationFunctionContext();
	static final Map<String, JsonNode> SYSTEM_VARIABLES = Collections.unmodifiableMap(new HashMap<String, JsonNode>());

	@BeforeEach
	def void setUp() {
		FUNCTION_CTX.loadLibrary(SimpleFunctionLibrary);
		FUNCTION_CTX.loadLibrary(FilterFunctionLibrary);
		FUNCTION_CTX.loadLibrary(new SimpleFilterFunctionLibrary(
			Clock.fixed(Instant.parse("2017-05-03T18:25:43.511Z"), ZoneId.of("Europe/Berlin"))
		));
	}

	@Test
	def void patientdataAnnotator() {
		// Annotators have to assign a context to given sensordata. It might be neccessary for them to get some information
		// about the patient for this task (e.g. skin resistance might vary depending on age and gender).
		// In the personal data section, age will be rounded to step of 5 and only the first digit of zip code is shown.
		// Gender is shown, all other values are removed.
		val authzSubscription = '''
		{  
		    "subject":{  
		        "id":"123456789012345678901212345678901234567890121234567890123456789012",
		        "isActive":true,
		        "role":"annotator"
		    },
		    "action":{  
		        "verb":"show_patientdata"
		    },
		    "resource":{  
				"patientid":"123456789012345678901212345678901234567890121234567890123456789999",
				"gender":"male",
				"firstname":"John",
				"lastname":"Doe",
				"age":68,
				"address":{  
				    "street":"Main Street 123",
				    "zip":"12345",
				    "city":"Anytown"
				      },
				      "medicalhistory_icd10":[
				      	"J45.8",
				      	"E10.90"
				      ]
				  },
				  "environment":{  
				      "ipAddress":"10.10.10.254"
				  }
		}''';
		val authzSubscription_object = MAPPER.readValue(authzSubscription, AuthorizationSubscription)

		val policyDefinition = '''
			policy "annotators_anonymize_patient" 
			permit 
				subject.role == "annotator" &
				action.verb == "show_patientdata"
			transform
				{
					"patientid" : resource.patientid,
					"gender" : resource.gender,
					"age" : resource.age |- simplefilter.roundto(5),
					"address" : {
						"zip" : resource.address.zip |- filter.blacken(1)
					}
				}
		''';

		var JsonNode expectedResource = null;
		try {
			expectedResource = MAPPER.readValue('''
				{ 
					"patientid":"123456789012345678901212345678901234567890121234567890123456789999",
					"gender":"male",
					"age":70,
					"address":{  
						"zip":"1XXXX"
					}
					   
				}
			''', JsonNode);
		} catch (Exception e) {
		}

		val expectedAuthzDecision = new AuthorizationDecision(Decision.PERMIT, Optional.of(expectedResource),
			Optional.empty(), Optional.empty())

		assertThat("anonymizing patient data for annotators not working as expected",
			INTERPRETER.evaluate(authzSubscription_object, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX,
				SYSTEM_VARIABLES).blockFirst(), equalTo(expectedAuthzDecision));
	}

	@Test
	def void patientdataDoctor() {

		val authzSubscription = '''
		{  
		    "subject":{  
		        "id":"123456789012345678901212345678901234567890121234567890123456789012",
		        "isActive":true,
		        "role":"doctor"
		    },
		    "action":{  
		        "verb":"show_patientdata"
		    },
		    "resource":{  
				"patientid":"123456789012345678901212345678901234567890121234567890123456789999",
				"gender":"male",
				"firstname":"John",
				"lastname":"Doe",
				"age":68,
				"address":{  
				    "street":"Main Street 123",
				    "zip":"12345",
				    "city":"Anytown"
				      },
				      "medicalhistory_icd10":[
				      	"J45.8",
				      	"E10.90"
				      ]
				  },
				  "environment":{  
				      "ipAddress":"10.10.10.254"
				  }
		}''';
		val authzSubscription_object = MAPPER.readValue(authzSubscription, AuthorizationSubscription)

		val policyDefinition = '''
			policy "doctors_hide_icd10" 
			permit 
				subject.role == "doctor" &
				action.verb == "show_patientdata"
			transform
				resource |- {
					@.address : filter.remove,
					each @.medicalhistory_icd10 : filter.blacken(1,0,"")
				}
		''';

		val expectedResource = MAPPER.readValue('''
			{
				"patientid":"123456789012345678901212345678901234567890121234567890123456789999",
				"gender":"male",
				"firstname":"John",
				"lastname":"Doe",
				"age":68,
				"medicalhistory_icd10":[
					"J",
					"E"
				]
			}
		''', JsonNode);

		assertThat("anonymizing patient data for doctors not working as expected",
			INTERPRETER.evaluate(authzSubscription_object, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX,
				SYSTEM_VARIABLES).blockFirst(),
			equalTo(
				new AuthorizationDecision(Decision.PERMIT, Optional.of(expectedResource), Optional.empty(),
					Optional.empty())));
	}

	@Test
	def void situationsFamilymember() {
		// There is a history of annotated contexts with sensordata, status, puppetreaction etc. for a patient.
		// This data might be used by a doctor in a consultation. However, a family member shall only have access
		// to the status of the latest entry.
		val authzSubscription = '''
		{  
		    "subject":{  
		        "id":"123456789012345678901212345678901234567890121234567890123456789012",
		        "isActive":true,
		        "role":"familymember"
		    },
		    "action":{  
		        "verb":"show_patient_situations"
		    },
		    "resource":{  
				"patientid":"123456789012345678901212345678901234567890121234567890123456789999",
				"detected_situations":[
					{
					     		"datetime":"2012-04-23T18:25:43.511Z",
					     		"captureid":"123456789012345678901212345678901234567890121234567890123456781234",
					     		"status":"OK",
					     		"situation":"NORMAL",
					     		"sensordata":{
					     		    "pulse":{  
					     		        "value":63
					     		    },
					     		    "skinresistance":{  
					     		        "value":205.6
					     		    },
					     		    "facialexpression":"foo"
					   	},
					   	"puppetaction":{
					   		"foo":"bar"
					   	}
					},
					{
					     		"datetime":"2012-04-23T19:27:41.327Z",
					     		"captureid":"123456789012345678901212345678901234567890121234567890123456781235",
					     		"status":"OK",
					     		"situation":"NORMAL",
					     		"sensordata":{
					     		    "pulse":{  
					     		        "value":66
					     		    },
					     		    "skinresistance":{  
					     		        "value":187.3
					     		    },
					     		    "facialexpression":"bar"
					   	},
					   	"puppetaction":{
							"foo":"bar"
						}
					}
				]
				  },
				  "environment":{  
				      "ipAddress":"10.10.10.254"
				  }
		}''';
		val authzSubscription_object = MAPPER.readValue(authzSubscription, AuthorizationSubscription)

		val policyDefinition = '''
		policy "familymembers_truncate_situations" 
		permit 
			subject.role == "familymember" &
			action.verb == "show_patient_situations"
		transform
			{
				"patientid" : resource.patientid,
				"detected_situations" : resource.detected_situations :: {
					"datetime" : @.datetime,
					"captureid" : @.captureid,
					"status" : @.status
				}
			} |- {
				@.detected_situations[1:] : filter.remove
			}''';

//    	// In case of an emergency, familymembers could have access to this data:
//    	var policyDefinitionEmergency = '''
//    		policy "familymembers_contexthistory_emergency" 
//    		permit 
//    			subject.role == "familymember" &&
//    			action.verb == "show_patient_contexthistory" &&
//    			environment.emergeny == true
//    		''';
//    	policyDefinitionEmergency = ''
//    	// Obligations/Advice ergänzen - jeweils als Array 
		val expectedResource = MAPPER.readValue('''
			{
				"patientid":"123456789012345678901212345678901234567890121234567890123456789999",
				"detected_situations":[
					{
					   	"datetime":"2012-04-23T18:25:43.511Z",
					 		"captureid":"123456789012345678901212345678901234567890121234567890123456781234",
					 			"status":"OK"
					}
				]
			}
		''', JsonNode);

		assertThat("truncating detected situations for familymembers not working as expected",
			INTERPRETER.evaluate(authzSubscription_object, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX,
				SYSTEM_VARIABLES).blockFirst(),
			equalTo(
				new AuthorizationDecision(Decision.PERMIT, Optional.of(expectedResource), Optional.empty(),
					Optional.empty())));
	}

	@Test
	def void situationsCaregiver() {
		val authzSubscription = '''
		{  
		    "subject":{  
		        "id":"123456789012345678901212345678901234567890121234567890123456789012",
		        "isActive":true,
		        "role":"professional_caregiver"
		    },
		    "action":{  
		        "verb":"show_patient_situations"
		    },
		    "resource":{  
				"patientid":"123456789012345678901212345678901234567890121234567890123456789999",
				"detected_situations":[
					{
					     		"datetime":"2012-04-23T18:25:43.511Z",
					     		"captureid":"123456789012345678901212345678901234567890121234567890123456781234",
					     		"status":"OK",
					     		"situation":"NORMAL",
					     		"sensordata":{
					     		    "pulse":{  
					     		        "value":63
					     		    },
					     		    "skinresistance":{  
					     		        "value":205.6
					     		    },
					     		    "facialexpression":"foo"
					   	},
					   	"puppetaction":{
					   		"foo":"bar"
					   	}
					},
					{
					     		"datetime":"2012-04-23T19:27:41.327Z",
					     		"captureid":"123456789012345678901212345678901234567890121234567890123456781235",
					     		"status":"OK",
					     		"situation":"NORMAL",
					     		"sensordata":{
					     		    "pulse":{  
					     		        "value":66
					     		    },
					     		    "skinresistance":{  
					     		        "value":187.3
					     		    },
					     		    "facialexpression":"bar"
					   	},
					   	"puppetaction":{
							"foo":"bar"
						}
					}
				]
				  },
				  "environment":{  
				      "ipAddress":"10.10.10.254"
				  }
		}''';
		val authzSubscription_object = MAPPER.readValue(authzSubscription, AuthorizationSubscription)

		// Assume professional_caregivers can view each entry, but without sensordata and puppet action
		val policyDefinition = '''
			policy "professional_caregiver_truncate_contexthistory" 
			permit 
				subject.role == "professional_caregiver" &
				action.verb == "show_patient_situations"
			transform
				resource |- {
					@.detected_situations.sensordata : filter.remove,
					@.detected_situations.puppetaction : filter.remove
				}
		''';

		val expectedResource = MAPPER.readValue('''
			{
				"patientid":"123456789012345678901212345678901234567890121234567890123456789999",
				"detected_situations":[
					{
						"datetime":"2012-04-23T18:25:43.511Z",
						"captureid":"123456789012345678901212345678901234567890121234567890123456781234",
						"status":"OK",
						"situation":"NORMAL"
					},
					{
						"datetime":"2012-04-23T19:27:41.327Z",
						"captureid":"123456789012345678901212345678901234567890121234567890123456781235",
						"status":"OK",
						"situation":"NORMAL"
					}
				]
			}
		''', JsonNode);

		assertThat("truncating detected situations for professional caregivers not working as expected",
			INTERPRETER.evaluate(authzSubscription_object, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX,
				SYSTEM_VARIABLES).blockFirst(),
			equalTo(
				new AuthorizationDecision(Decision.PERMIT, Optional.of(expectedResource), Optional.empty(),
					Optional.empty())));
	}

	@Test
	def void situationsPuppetIntroducer() {
		val authzSubscription = '''
		{  
		    "subject":{  
		        "id":"123456789012345678901212345678901234567890121234567890123456789012",
		        "isActive":true,
		        "role":"puppet_introducer"
		    },
		    "action":{  
		        "verb":"show_patient_situations"
		    },
		    "resource":{  
				"patientid":"123456789012345678901212345678901234567890121234567890123456789999",
				"detected_situations":[
					{
					   		"datetime":"2017-05-03T18:25:43.511Z",
					   		"captureid":"123456789012345678901212345678901234567890121234567890123456781234",
					   		"status":"OK",
					   		"situation":"NORMAL",
					   		"sensordata":{
					   		    "pulse":{  
					   		        "value":63
					   		    },
					   		    "skinresistance":{  
					   		        "value":205.6
					   		    },
					   		    "facialexpression":"foo"
					   	},
					   	"puppetaction":{
					   		"foo":"bar"
					   	}
					},
					{
					   		"datetime":"2012-04-23T19:27:41.327Z",
					   		"captureid":"123456789012345678901212345678901234567890121234567890123456781235",
					   		"status":"OK",
					   		"situation":"NORMAL",
					   		"sensordata":{
					   		    "pulse":{  
					   		        "value":66
					   		    },
					   		    "skinresistance":{  
					   		        "value":187.3
					   		    },
					   		    "facialexpression":"bar"
					   	},
					   	"puppetaction":{
							"foo":"bar"
						}
					}
				]
				  },
				  "environment":{  
				      "ipAddress":"10.10.10.254"
				  }
		}''';
		val authzSubscription_object = MAPPER.readValue(authzSubscription, AuthorizationSubscription)

		// Let's assume puppet introducers can access only the contexts from the same day:
		val policyDefinition = '''
			policy "puppetintroducers_truncate_situations" 
				permit 
					subject.role == "puppet_introducer" &
					action.verb == "show_patient_situations"
				transform
					{
						"patientid" : resource.patientid,
						"detected_situations" : resource.detected_situations[?(simplefilter.isOfToday(@.datetime))] :: {
							"datetime" : @.datetime,
							"captureid" : @.captureid,
							"status" : @.status
						}
					}
		''';

		val expectedResource = MAPPER.readValue('''
			{
				"patientid":"123456789012345678901212345678901234567890121234567890123456789999",
				"detected_situations":[
					{
						"datetime":"2017-05-03T18:25:43.511Z",
						"captureid":"123456789012345678901212345678901234567890121234567890123456781234",
						"status":"OK"
					}
				]
			}
		''', JsonNode);

		assertThat("truncating detected situations for puppetintroducers not working as expected",
			INTERPRETER.evaluate(authzSubscription_object, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX,
				SYSTEM_VARIABLES).blockFirst(),
			equalTo(
				new AuthorizationDecision(Decision.PERMIT, Optional.of(expectedResource), Optional.empty(),
					Optional.empty())));
	}
}

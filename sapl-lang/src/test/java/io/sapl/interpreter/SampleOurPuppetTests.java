/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.interpreter;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.functions.FilterFunctionLibrary;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import lombok.SneakyThrows;

class SampleOurPuppetTests {
    private static final ObjectMapper               MAPPER           = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final DefaultSAPLInterpreter     INTERPRETER      = new DefaultSAPLInterpreter();
    private static final AnnotationAttributeContext ATTRIBUTE_CTX    = new AnnotationAttributeContext();
    private static final AnnotationFunctionContext  FUNCTION_CTX     = new AnnotationFunctionContext();
    private static final Map<String, Val>           SYSTEM_VARIABLES = Collections.unmodifiableMap(new HashMap<>());

    @BeforeEach
    public void setUp() throws InitializationException {
        FUNCTION_CTX.loadLibrary(SimpleFunctionLibrary.class);
        FUNCTION_CTX.loadLibrary(FilterFunctionLibrary.class);
        FUNCTION_CTX.loadLibrary(new SimpleFilterFunctionLibrary(
                Clock.fixed(Instant.parse("2017-05-03T18:25:43.511Z"), ZoneId.of("Europe/Berlin"))));
    }

    private static Stream<Arguments> provideTestCases() {
        // @formatter:off
        return Stream.of(Arguments.of("patientdataAnnotator", """
                An annotating person has to assign a context to given sensor data. It might
                be necessary for them to get some information about the patient for this task
                (e.g. skin resistance might vary depending on age and gender).
                In the personal data section, age will be rounded to step of 5 and only the
                first digit of ZIP code is shown. Gender is shown, all other values are removed.
                ""","""
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
                }""",
                """
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
                ""","""
                {
                    "patientid":"123456789012345678901212345678901234567890121234567890123456789999",
                    "gender":"male",
                    "age":70,
                    "address":{
                        "zip":"1XXXX"
                    }
                }
                """                
                ),
                Arguments.of("patientdataDoctor","",
                        """
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
                        }""",
                        """
                          policy "doctors_hide_icd10"
                          permit
                              subject.role == "doctor" &
                              action.verb == "show_patientdata"
                          transform
                              resource |- {
                                  @.address : filter.remove,
                                  each @.medicalhistory_icd10 : filter.blacken(1,0,"")
                              }
                        """,
                        """
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
                        }"""                      
                        ),
                Arguments.of("situationsFamilymember",
                        """
                        There is a history of annotated contexts with sensor data, status,
                        puppet reaction etc. for a patient. This data might be used by a 
                        doctor in a consultation. However, a family member shall only have 
                        access to the status of the latest entry.
                        """,
                        """
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
                        }""",
                        """
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
                            }""",
                        """
                        {
                            "patientid":"123456789012345678901212345678901234567890121234567890123456789999",
                            "detected_situations":[
                                {
                                    "datetime":"2012-04-23T18:25:43.511Z",
                                        "captureid":"123456789012345678901212345678901234567890121234567890123456781234",
                                            "status":"OK"
                                }
                            ]
                        }"""
                        ),
                Arguments.of("situationsCaregiver",
                        "Assume professional_caregivers can view each entry, but without sensor data and puppet action",
                        """
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
                                }""",
                                """
                                policy "professional_caregiver_truncate_contexthistory"
                                    permit
                                        subject.role == "professional_caregiver" &
                                        action.verb == "show_patient_situations"
                                    transform
                                        resource |- {
                                            @.detected_situations.sensordata : filter.remove,
                                            @.detected_situations.puppetaction : filter.remove
                                        }""",
                                """
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
                                }"""                        
                        ),
                Arguments.of("situationsPuppetIntroducer",
                        "Let's assume puppet introducers can access only the contexts from the same day",
                        """
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
                        }""",
                        """
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
                        """,
                        """
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
                        """
                        )
        );
        // @formatter:off
    }
    
    @SneakyThrows
    @ParameterizedTest
    @MethodSource("provideTestCases")
    void evaluateOurPuppetTestCase(String testCase, String description, String authorizationSubscription, String policy,String expectedResource) {
        assertThat(testCase, is(notNullValue()));
        assertThat(description, is(notNullValue()));
        var expectedDecision = AuthorizationDecision.PERMIT
                .withResource(MAPPER.readValue(expectedResource, JsonNode.class));
        assertThat(INTERPRETER.evaluate(MAPPER.readValue(authorizationSubscription, AuthorizationSubscription.class),
                policy, ATTRIBUTE_CTX, FUNCTION_CTX, SYSTEM_VARIABLES).blockFirst(), equalTo(expectedDecision));
    }

}

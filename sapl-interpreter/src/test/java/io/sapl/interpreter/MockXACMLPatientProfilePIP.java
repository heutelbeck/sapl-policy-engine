/**
 * Copyright © 2017 Dominic Heutelbeck (dheutelbeck@ftk.de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.sapl.interpreter;

import java.io.IOException;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import io.sapl.api.pip.Attribute;
import io.sapl.api.pip.PolicyInformationPoint;

@PolicyInformationPoint(name = MockXACMLPatientProfilePIP.NAME)
public class MockXACMLPatientProfilePIP {

	public static final String NAME = "patient";

	private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

	@Attribute
	public JsonNode profile(JsonNode value, Map<String, JsonNode> variables) throws IOException {
		String json = "{" + "	\"patient\": {" + "		\"name\": {" + "			\"first\": \"Bartholomew\","
				+ "			\"last\": \"Simpson\"" + "		}," + "		\"contact\": {"
				+ "			\"street\": \"27 Shelbyville Road\"," + "			\"email\": null" + "		},"
				+ "		\"DoB\": \"1992-03-21\"," + "		\"patient_number\": \"555555\"" + "	},"
				+ "	\"parentGuardian\": {" + "		\"id\": \"HS001\"," + "		\"name\": {"
				+ "			\"first\": \"Homer\"," + "			\"last\": \"Simpson\"" + "		},"
				+ "		\"contact\": {" + "			\"email\": \"homers@aol.com\"" + "		}" + "	},"
				+ "	\"primaryCarePhysician\": {" + "		\"contact\": { }," + "		\"registrationID\": \"ABC123\""
				+ "	}," + "	\"medical\": {" + "		\"treatment\": {" + "			\"drug\": {"
				+ "				\"name\": \"methylphenidate hydrochloride\","
				+ "				\"dailyDosage\": \"30mgs\"," + "				\"startDate\": \"1999-01-12\""
				+ "			},"
				+ "			\"comment\": \"patient exhibits side-effects of skin coloration and carpal degeneration\""
				+ "		}," + "		\"result\": {" + "			\"test\": \"blood pressure\","
				+ "			\"value\": \"120/80\"," + "			\"date\": \"2001-06-09\","
				+ "			\"performedBy\": \"Nurse Betty\"" + "		}" + "	}" + "}";

		return MAPPER.readTree(json);
	}
}

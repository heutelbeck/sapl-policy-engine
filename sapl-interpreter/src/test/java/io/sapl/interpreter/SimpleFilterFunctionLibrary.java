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

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionException;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.validation.Int;
import io.sapl.api.validation.Text;

@FunctionLibrary(name = "simplefilter", description = "some simple filter functions")
public class SimpleFilterFunctionLibrary {
    private static final JsonNodeFactory json = JsonNodeFactory.instance;

    private Clock clock;

    public SimpleFilterFunctionLibrary(
            Clock clock
    ) {
        this.clock = clock;
    }

	@Function(name = "roundto")
	public static JsonNode roundto(
			@Int JsonNode node, @Int JsonNode roundvalue
    ) throws FunctionException {
		int value = node.asInt();
		int round = roundvalue.asInt();

        int result = value / round;
        if (value % round > round / 2) {
            result++;
        }

        return json.numberNode(result * round);
    }

    @Function
	public JsonNode isOfToday(
            @Text JsonNode parameter
    ) throws FunctionException {
        LocalDate today = LocalDate.now(clock);
        Instant instant = Instant.parse(parameter.asText());
        ZoneId zone = ZoneId.of("Europe/Berlin");
        ZonedDateTime zdt = instant.atZone(zone);

        if (today.getYear() == zdt.getYear()
                && today.getMonthValue() == zdt.getMonthValue()
                && today.getDayOfMonth() == zdt.getDayOfMonth()) {
            return json.booleanNode(true);
        } else {
            return json.booleanNode(false);
        }
    }

}

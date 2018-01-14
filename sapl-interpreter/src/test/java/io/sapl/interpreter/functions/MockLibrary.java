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
package io.sapl.interpreter.functions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;

@FunctionLibrary(name = MockLibrary.NAME, description = "docs of my lib")
public class MockLibrary {
    public static final String NAME = "test.lib";

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    @Function(docs = "docs for helloTest")
    public static JsonNode helloTest() {

        return JSON.textNode("HELLO TEST");
    }

}

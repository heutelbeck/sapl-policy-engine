/*******************************************************************************
 * Copyright 2017-2018 Dominic Heutelbeck (dheutelbeck@ftk.de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy
 * of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 ******************************************************************************/
package io.sapl.benchmark;

import java.util.ArrayList;
import java.util.List;

import lombok.Builder;

@Builder
public class TestSuite {

	@Builder.Default
    private List<PolicyGeneratorConfiguration> cases = new ArrayList<>();

    public TestSuite() {
    }

    public TestSuite(List<PolicyGeneratorConfiguration> cases) {
        this.cases = cases;
    }

    public List<PolicyGeneratorConfiguration> getCases() {
        return cases;
    }

    public void setCases(List<PolicyGeneratorConfiguration> cases) {
        this.cases = cases;
    }
}

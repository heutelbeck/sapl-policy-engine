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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class PolicyGeneratorConfiguration {

	@Builder.Default
    private String name = "";

	@Builder.Default
    private long seed = 0L;

	@Builder.Default
    private int policyCount = 0;

	@Builder.Default
    private int logicalVariableCount = 0;

	@Builder.Default
    private int variablePoolCount = 0;

	@Builder.Default
    private double bracketProbability = 0D;

	@Builder.Default
    private double conjunctionProbability = 0D;

	@Builder.Default
    private double negationProbability = 0D;

	@Builder.Default
    private double falseProbability = 0D;

	@Builder.Default
    private String path = "";

    public void updateName() {
        this.name = String
                .format("\"%dp, %dv, %dvp\"", this.policyCount, this.logicalVariableCount, this.variablePoolCount);
    }


}

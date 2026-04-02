/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.benchmark.sapl4.oopsla;

import io.sapl.api.pdp.CombiningAlgorithm;
import io.sapl.api.pdp.CombiningAlgorithm.DefaultDecision;
import io.sapl.api.pdp.CombiningAlgorithm.ErrorHandling;
import io.sapl.api.pdp.CombiningAlgorithm.VotingMode;
import lombok.experimental.UtilityClass;

/**
 * Constants shared across all OOPSLA 2024 Cedar-equivalent scenario
 * generators. Values match Cedar's benchmark configuration
 * (cedar-examples/oopsla2024-benchmarks/).
 */
@UtilityClass
public final class OopslaConstants {

    // Cedar: 500 requests per hierarchy (main.rs --num-requests 500)
    public static final int REQUESTS_PER_GRAPH = 500;

    // Cedar: connection_probability = 0.05 for all generators (apps.rs)
    public static final double EDGE_PROBABILITY = 0.05;

    public static final CombiningAlgorithm ALGORITHM = new CombiningAlgorithm(VotingMode.PRIORITY_DENY,
            DefaultDecision.DENY, ErrorHandling.PROPAGATE);

    public static final String PREFIX_DOC    = "Document::doc_";
    public static final String PREFIX_FOLDER = "Folder::folder_";
    public static final String PREFIX_GROUP  = "Group::group_";
    public static final String PREFIX_LIST   = "List::list_";
    public static final String PREFIX_ORG    = "Organization::org_";
    public static final String PREFIX_REPO   = "Repository::repo_";
    public static final String PREFIX_TEAM   = "Team::team_";
    public static final String PREFIX_USER   = "User::user_";

}

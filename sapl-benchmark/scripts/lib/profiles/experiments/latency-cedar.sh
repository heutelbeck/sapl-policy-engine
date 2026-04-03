#
# Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
#
# SPDX-License-Identifier: Apache-2.0
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# Experiment: Cedar OOPSLA equivalent latency benchmarks
# 200 seeds x 7 scaling factors x 3 Cedar scenarios

SEEDS=200
SCALING_FACTORS=(5 10 15 20 30 40 50)
APPS=(tinytodo gdrive github)
INDEXING_SWEEP=(AUTO)
UNROLL_SWEEP=(false)
GC_SWEEP=(default)

# Latency experiments use looser convergence (many seeds average out noise)
WARMUP_TIME=10
CONVERGENCE_THRESHOLD=50

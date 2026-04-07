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

# Experiment: index strategy comparison across all scenarios
# NAIVE vs CANONICAL vs SMTDD, single thread, decideOnceBlocking
# Purpose: verify correctness and compare throughput across all index types

SCENARIOS=(baseline rbac hospital-1 hospital-5 hospital-50 hospital-100 hospital-300 github-10 github-100 gdrive-10 tinytodo-10)
METHODS=(decideOnceBlocking)
THREAD_SWEEP=(1)
INDEXING_SWEEP=(NAIVE CANONICAL SMTDD)

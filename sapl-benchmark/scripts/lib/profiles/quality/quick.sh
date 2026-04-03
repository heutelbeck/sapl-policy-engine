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

# Quality profile: quick
# Fast iteration, end-to-end validation. Runs in 2-5 minutes.

WARMUP_ITERATIONS=1
WARMUP_TIME=3
MEASUREMENT_TIME=10
CONVERGENCE_THRESHOLD=10
CONVERGENCE_WINDOW=2
MAX_FORKS=2
COOL_TARGET=90
WRK_WARMUP_TIME=3
WRK_MEASURE_TIME=10
WRK_CONVERGE=true
LATENCY=true

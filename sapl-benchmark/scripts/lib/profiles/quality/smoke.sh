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

# Quality profile: smoke
# Fastest possible sweep. Verifies index builds and returns results
# without caring about stable numbers. Runs in under 2 minutes.

WARMUP_ITERATIONS=0
WARMUP_TIME=1
MEASUREMENT_TIME=3
CONVERGENCE_THRESHOLD=100
CONVERGENCE_WINDOW=1
MAX_FORKS=1
COOL_TARGET=90
WRK_WARMUP_TIME=1
WRK_MEASURE_TIME=3
WRK_CONVERGE=false
LATENCY=false

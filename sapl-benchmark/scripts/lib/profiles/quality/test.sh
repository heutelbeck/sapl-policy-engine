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

# Quality profile: test
# Smallest possible run that exercises every benchmark script end to end. Used
# by CI to catch breakage in the scripts themselves, not to produce numbers.
# Runs on any machine: pins nothing and never waits to cool. MINIMAL truncates
# every parameter sweep to a single combination, so each experiment runs once.

WARMUP_ITERATIONS=0
WARMUP_TIME=1
MEASUREMENT_TIME=1
CONVERGENCE_THRESHOLD=100
CONVERGENCE_WINDOW=1
MAX_FORKS=1
COOL_TARGET=999
WRK_WARMUP_TIME=1
WRK_MEASURE_TIME=1
WRK_CONVERGE=false
LATENCY=false

# Keep latency-at-load cheap in CI. This profile checks script wiring, not numbers.
LATENCY_SERVER_GC="-Xmx512m"
SCENARIOS_QUICK=(baseline)
RSOCKET_CONNECTIONS=1
RSOCKET_CONCURRENCY=8
RSOCKET_SATURATION_WARMUP_SECONDS=0
RSOCKET_SATURATION_MEASUREMENT_SECONDS=1
HTTP_LAT_CONNECTIONS=8
LATENCY_AT_LOAD_RUNTIMES=(jvm)
WRK_THREADS=1

# Run anywhere: a CI runner has arbitrary CPU topology, so the fixed pinning
# ranges would fail. The thermal target is unreachable, so cooling returns at
# once.
MINIMAL=true
PINNING_AVAILABLE=false

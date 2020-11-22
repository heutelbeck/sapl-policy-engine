/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.interpreter.pip.geo;

import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.Data;

@Data
@JsonInclude(Include.NON_NULL)
public class TraccarDevice {

	private int id;

	private JsonNode attributes;

	private String name;

	private String uniqueId;

	private String status;

	private String lastUpdate;

	private int positionId;

	private int groupId;

	private int[] geofenceIds;

	private String phone;

	private String model;

	private String contact;

	private Object category;

	// Don't expose the internal representation by storing an externally mutable
	// object
	// into geofenceIds

	public int[] getGeofenceIds() {
		return geofenceIds != null ? Arrays.copyOf(geofenceIds, geofenceIds.length) : null;
	}

	public void setGeofenceIds(int[] geofenceIds) {
		this.geofenceIds = geofenceIds != null ? Arrays.copyOf(geofenceIds, geofenceIds.length) : null;
	}

}

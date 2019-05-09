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
package io.sapl.interpreter.pip.geo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.Data;

@Data
@JsonInclude(Include.NON_NULL)
public class TraccarPosition {

	private int id;

	private JsonNode attributes;

	private int deviceId;

	private Object type;

	private String protocol;

	private String serverTime;

	private String deviceTime;

	private String fixTime;

	private boolean outdated;

	private boolean valid;

	private double latitude;

	private double longitude;

	private double altitude;

	private double speed;

	private double course;

	private String address;

	private double accuracy;

	private Object network;

	public static int compareAscending(TraccarPosition a, TraccarPosition b) {
		if (a.getId() > b.getId()) {
			return +1;
		}
		else if (a.getId() < b.getId()) {
			return -1;
		}
		else {
			return 0;
		}
	}

	public static int compareDescending(TraccarPosition a, TraccarPosition b) {
		if (a.getId() > b.getId()) {
			return -1;
		}
		else if (a.getId() < b.getId()) {
			return +1;
		}
		else {
			return 0;
		}
	}

}

package io.sapl.interpreter.pip.geo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(Include.NON_NULL)
public class TraccarConfig {
	private String deviceID;
	private String url;
	private String credentials; // already Base64-encrypted
	private String username;
	private String password;
	private int posValidityTimespan = 120;
}

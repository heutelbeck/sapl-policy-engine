package io.sapl.attributeapigui.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AttributeApiConnectionPropertiesTests {
	AttributeApiConnectionProperties properties = new AttributeApiConnectionProperties();
	
	@Test
	@DisplayName("When a URL is blank then throw an exception that the base url is not set.")
	void whenURLIsBlankThenThrowAnException() {
		properties.setBaseUrl("");
		assertThatThrownBy(() -> properties.afterPropertiesSet())
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("The base url is not set. Please set io.sapl.attribute-api-gui.connection.base-url via settings");
	}
	
	@Test
	@DisplayName("When the URL is malformed then throw an exception thate url is malformed and not a valid url")
	void whenURLIsMalformedThenThrowAnException() {
		properties.setBaseUrl("test___");
		assertThatThrownBy(() -> properties.afterPropertiesSet())
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("The given url is not a valid url");
	}
}

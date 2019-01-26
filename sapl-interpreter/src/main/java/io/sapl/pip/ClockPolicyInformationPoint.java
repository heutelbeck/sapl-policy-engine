package io.sapl.pip;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.pip.Attribute;
import io.sapl.api.pip.AttributeException;
import io.sapl.api.pip.PolicyInformationPoint;
import io.sapl.api.validation.Number;
import io.sapl.api.validation.Text;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Flux;

@NoArgsConstructor
@PolicyInformationPoint(name = ClockPolicyInformationPoint.NAME, description = ClockPolicyInformationPoint.DESCRIPTION)
public class ClockPolicyInformationPoint {

    public static final String NAME = "clock";
    public static final String DESCRIPTION = "Policy Information Point and attributes for retrieving current date and time information";

    @Attribute(docs = "Returns the current date and time in the given time zone (e.g. 'UTC', 'ECT', 'Europe/Berlin', 'system') as an ISO-8601 string with time offset.")
    public JsonNode now(@Text JsonNode value, Map<String, JsonNode> variables) throws AttributeException {
        try {
            final ZoneId zoneId = convertToZoneId(value);
            final OffsetDateTime now = Instant.now().atZone(zoneId).toOffsetDateTime();
            return JsonNodeFactory.instance.textNode(now.toString());
        } catch (Exception e) {
            throw new AttributeException("Exception while converting the given value to a ZoneId.", e);
        }
    }

    private ZoneId convertToZoneId(JsonNode value) {
        final String text = value.asText();
        final String zoneIdStr = (text == null || text.trim().length() == 0) ? "system" : text.trim();
        if ("system".equals(zoneIdStr)) {
            return ZoneId.systemDefault();
        } else if (ZoneId.SHORT_IDS.containsKey(zoneIdStr)) {
            return ZoneId.of(zoneIdStr, ZoneId.SHORT_IDS);
        }
        return ZoneId.of(zoneIdStr);
    }

    @Attribute(docs = "Emits every x seconds the current UTC date and time as an ISO-8601 string. x is the passed number value.", reactive = true)
    public Flux<JsonNode> ticker(@Number JsonNode value, Map<String, JsonNode> variables) throws AttributeException {
        try {
            return Flux.interval(Duration.ofSeconds(value.asLong()))
                    .map(i -> JsonNodeFactory.instance.textNode(Instant.now().toString()));
        } catch (Exception e) {
            throw new AttributeException("Exception while creating the next ticker value.", e);
        }
    }
}

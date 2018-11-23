package io.sapl.pip;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.sapl.api.pip.Attribute;
import io.sapl.api.pip.AttributeException;
import io.sapl.api.pip.PolicyInformationPoint;
import io.sapl.api.validation.JsonObject;
import io.sapl.api.validation.Text;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@PolicyInformationPoint(name = ClockPolicyInformationPoint.NAME, description = ClockPolicyInformationPoint.DESCRIPTION)
public class ClockPolicyInformationPoint {

    public static final String NAME = "clock";
    public static final String DESCRIPTION = "Policy Information Point and attributes for retrieving current date and time information";

    @Attribute(docs = "Returns the current time in the given time zone (e.g. 'UTC', 'ECT', 'Europe/Berlin', 'system') as an ISO 8601 string.")
    public JsonNode now(@Text @JsonObject JsonNode value, Map<String, JsonNode> variables) throws AttributeException {
        try {
            final ZoneId zoneId = convertToZoneId(value);
            final OffsetDateTime now = OffsetDateTime.now(zoneId);
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

    public static void main(String[] args) {
        final OffsetDateTime now = OffsetDateTime.now(ZoneId.of("UTC"));
        System.out.println(now.getDayOfWeek().toString());
        final LocalDateTime localDateTime = LocalDateTime.parse(now.toString(), DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC));
        System.out.println(localDateTime.toString());
    }
}

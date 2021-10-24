package io.sapl.test.unit.usecase;

import static io.sapl.hamcrest.Matchers.*;
import static io.sapl.test.Imports.whenFunctionParams;
import static org.hamcrest.CoreMatchers.*;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.test.SaplTestFixture;
import io.sapl.test.unit.SaplUnitTestFixture;

import org.junit.jupiter.api.BeforeEach;

public class Z_ComplexUnitTest {

	private SaplTestFixture fixture;
	
	@BeforeEach
	public void setUp() {
		fixture = new SaplUnitTestFixture("policyComplex");
	}

	
	//@Test
	//@Disabled
	//only to test readability of a complex test case
	void test_complexPolicy() {
		var timestamp0 = Val.of(ZonedDateTime.of(2021, 1, 7, 0, 0, 0, 0, ZoneId.of("UTC")).toString());
		var timestamp1 = Val.of(ZonedDateTime.of(2021, 1, 8, 0, 0, 1, 0, ZoneId.of("UTC")).toString());
		var timestamp2 = Val.of(ZonedDateTime.of(2021, 1, 9, 0, 0, 2, 0, ZoneId.of("UTC")).toString());
			
		fixture.constructTestCaseWithMocks()
			.givenAttribute("clock.ticker", timestamp0, timestamp1, timestamp2)
			.givenFunctionOnce("time.dayOfWeekFrom", Val.of("SATURDAY"), Val.of("SUNDAY"), Val.of("MONDAY"))
			.givenAttribute("company.reportmode", Val.of("ALL"))
			.givenFunction("company.subjectConverter", whenFunctionParams(is(Val.of("ADMIN")), anyVal()), Val.of("ROLE_ADMIN"))
			.givenFunction("company.subjectConverter", whenFunctionParams(is(Val.of("USER")), is(Val.of("nikolai"))), Val.of("ROLE_ADMIN"))
			.givenFunction("company.subjectConverter", whenFunctionParams(is(Val.of("USER")), anyVal()), Val.of("ROLE_USER"))
			.when(AuthorizationSubscription.of("nikolai", "read", "report"))
			.expectNextDeny(2)
			.expectNext(
					allOf(
						isPermit(),
						hasObligationMatching(ob -> ob.get("type").asText().equals("logAccess")),
						isResourceMatching(jsonNode -> {
							return jsonNode.has("report")
									&& jsonNode.get("report").has("numberOfCurrentPatients")
									&& jsonNode.get("report").get("numberOfCurrentPatients").asInt() == 34;
						})
					))
			.verify();
	}
}

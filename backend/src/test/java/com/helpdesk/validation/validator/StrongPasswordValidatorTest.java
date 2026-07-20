package com.helpdesk.validation.validator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrongPasswordValidatorTest {

    private final StrongPasswordValidator validator = new StrongPasswordValidator();

    @Test
    void shouldAccept_null_leavingPresenceToNotBlank() {
        assertTrue(validator.isValid(null, null));
    }

    @Test
    void shouldAccept_aPasswordMeetingEveryRule() {
        assertTrue(validator.isValid("Str0ng!Passw0rd", null));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Ab1!",              // too short
            "alllowercase1!",    // no uppercase
            "ALLUPPERCASE1!",    // no lowercase
            "NoDigitsHere!!",    // no digit
            "NoSymbol1234ab",    // no symbol
            "",                  // empty
    })
    void shouldReject_passwordsMissingARequiredRule(String candidate) {
        assertFalse(validator.isValid(candidate, null));
    }
}

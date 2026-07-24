package com.helpdesk.validation.validator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void shouldAccept_aPasswordAtExactlyTheMaximumLength() {
        String password = "Aa1!" + "a".repeat(124);

        assertEquals(128, password.length());
        assertTrue(validator.isValid(password, null));
    }

    @Test
    void shouldReject_aPasswordOneCharacterOverTheMaximumLength() {
        // Milestone 6 hardening - see StrongPasswordValidator's own Javadoc
        // for why an unbounded maximum is itself a weakness.
        String password = "Aa1!" + "a".repeat(125);

        assertEquals(129, password.length());
        assertFalse(validator.isValid(password, null));
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

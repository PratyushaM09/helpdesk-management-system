package com.helpdesk;

import com.helpdesk.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
class HelpDeskManagementSystemApplicationTests {

    @Autowired
    private GlobalExceptionHandler globalExceptionHandler;

    @Test
    void contextLoads() {
    }

    /**
     * Durable proof that the milestone 3 exception handler is actually
     * wired into the Spring context via component scanning — not just
     * "doesn't throw," but explicitly present as a bean. Guards against a
     * future accidental removal of @RestControllerAdvice or a component
     * scan exclusion silently disabling it.
     */
    @Test
    void globalExceptionHandlerIsRegisteredAsABean() {
        assertNotNull(globalExceptionHandler);
    }

}

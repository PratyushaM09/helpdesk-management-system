package com.helpdesk.auth.service;

import com.helpdesk.auth.dto.response.LoginResponse;
import org.springframework.http.ResponseCookie;

import java.util.List;

/**
 * Internal carrier returned by {@link AuthenticationService} — never itself
 * serialized to the wire. Bundles the JSON body ({@link LoginResponse}) with
 * the {@code Set-Cookie} values the Controller (not built yet) attaches to
 * the HTTP response; the Service layer builds cookie values but must never
 * touch {@code HttpServletResponse} itself (02-Architecture.md §3), which is
 * exactly the boundary this type sits on.
 */
public record AuthenticationResult(LoginResponse response, List<ResponseCookie> cookies) {
}

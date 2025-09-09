package com.split.ai.split.service.server.controller;

import com.split.ai.split.service.core.service.IUserAuthService;
import com.split.ai.split.service.model.request.userauth.LoginRequest;
import com.split.ai.split.service.model.request.userauth.SignupRequest;
import com.split.ai.split.service.model.response.userauth.LoginResponse;
import com.split.ai.split.service.model.response.userauth.SignupResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletResponse;

@Slf4j
@RestController
@RequestMapping("/v1/user-auth")
@RequiredArgsConstructor
public class UserAuthController {

    private final IUserAuthService userAuthService;

    @PostMapping("/signup")
    public SignupResponse signup(@Valid @RequestBody SignupRequest request, HttpServletResponse response) {
        log.info("[UserAuthController : signup] : userName={}", request.getUserName());
        SignupResponse resp = userAuthService.signup(request);
        setCookies(response, resp.getAccessToken(), resp.getRefreshToken());
        return resp;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        log.info("[UserAuthController : login] : identifier={}", request.getIdentifier());
        LoginResponse resp = userAuthService.login(request);
        setCookies(response, resp.getAccessToken(), resp.getRefreshToken());
        return resp;
    }

    @PostMapping("/logout")
    public void logout(@CookieValue(value = "RefreshToken", required = false) String refreshToken,
                       HttpServletResponse response) {
        log.info("[UserAuthController : logout] : noop");
        userAuthService.logout(refreshToken);
        clearCookies(response);
    }

    private void setCookies(HttpServletResponse response, String accessToken, String refreshToken) {
        if (accessToken == null || refreshToken == null) {
            return;
        }
        ResponseCookie accessCookie = ResponseCookie.from("AccessToken", accessToken)
                .httpOnly(true)
                .path("/")
                .build();
        ResponseCookie refreshCookie = ResponseCookie.from("RefreshToken", refreshToken)
                .httpOnly(true)
                .path("/")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
    }

    private void clearCookies(HttpServletResponse response) {
        ResponseCookie accessCookie = ResponseCookie.from("AccessToken", "")
                .path("/")
                .maxAge(0)
                .build();
        ResponseCookie refreshCookie = ResponseCookie.from("RefreshToken", "")
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
    }
}

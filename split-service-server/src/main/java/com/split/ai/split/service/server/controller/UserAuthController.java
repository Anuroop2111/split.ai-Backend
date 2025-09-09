package com.split.ai.split.service.server.controller;

import com.split.ai.split.service.core.service.IUserAuthService;
import com.split.ai.split.service.model.request.userauth.LoginRequest;
import com.split.ai.split.service.model.request.userauth.SignupRequest;
import com.split.ai.split.service.model.response.userauth.LoginResponse;
import com.split.ai.split.service.model.response.userauth.SignupResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/v1/user-auth")
@RequiredArgsConstructor
public class UserAuthController {

    private final IUserAuthService userAuthService;

    @PostMapping("/signup")
    public SignupResponse signup(@Valid @RequestBody SignupRequest request, HttpServletResponse response) {
        log.info("[UserAuthController : signup] : userName={}", request.getUserName());
        return userAuthService.signup(request, response);
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        log.info("[UserAuthController : login] : identifier={}", request.getIdentifier());
        return userAuthService.login(request, response);
    }

    @PostMapping("/logout")
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        log.info("[UserAuthController : logout] : noop");
        userAuthService.logout(request, response);
    }
}

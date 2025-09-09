package com.split.ai.split.service.core.service;

import com.split.ai.split.service.model.request.userauth.LoginRequest;
import com.split.ai.split.service.model.request.userauth.SignupRequest;
import com.split.ai.split.service.model.response.userauth.LoginResponse;
import com.split.ai.split.service.model.response.userauth.SignupResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface IUserAuthService {

    SignupResponse signup(SignupRequest request, HttpServletResponse response);

    LoginResponse login(LoginRequest request, HttpServletResponse response);

    void logout(HttpServletRequest request, HttpServletResponse response);
}

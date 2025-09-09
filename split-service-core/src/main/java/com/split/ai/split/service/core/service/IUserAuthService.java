package com.split.ai.split.service.core.service;

import com.split.ai.split.service.model.request.userauth.LoginRequest;
import com.split.ai.split.service.model.request.userauth.SignupRequest;
import com.split.ai.split.service.model.response.userauth.LoginResponse;
import com.split.ai.split.service.model.response.userauth.SignupResponse;

public interface IUserAuthService {

    SignupResponse signup(SignupRequest request);

    LoginResponse login(LoginRequest request);

    void logout(String refreshToken);
}

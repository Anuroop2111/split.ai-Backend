package com.split.ai.split.service.model.response.userauth;

import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignupResponse {

    private UUID userId;
    private String userName;
    @JsonIgnore
    private String accessToken;
    @JsonIgnore
    private String refreshToken;
}

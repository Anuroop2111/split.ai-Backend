package com.split.ai.split.service.core.mapper;

import com.split.ai.split.service.model.request.userauth.SignupRequest;
import com.split.ai.split.service.model.response.userauth.LoginResponse;
import com.split.ai.split.service.model.response.userauth.SignupResponse;
import com.split.ai.split.service.repository.entity.IdentityEntity;
import com.split.ai.split.service.repository.entity.LocalCredentialsEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.UUID;

@Mapper
public interface UserAuthServiceMapper extends BaseServiceMapper {

    UserAuthServiceMapper MAPPER = Mappers.getMapper(UserAuthServiceMapper.class);

    @Mapping(target = "userId", source = "userId")
    @Mapping(target = "userName", source = "request.userName")
    SignupResponse toSignupResponse(SignupRequest request, UUID userId);

    @Mapping(target = "userName", source = "identifier")
    LoginResponse toLoginResponse(IdentityEntity identityEntity);

    @Mapping(target = "identityId", source = "request", qualifiedByName = "randomUUID")
    @Mapping(target = "userId", source = "request", qualifiedByName = "randomUUID")
    @Mapping(target = "provider", source = "request.provider")
    @Mapping(target = "identifier", source = "request.emailId")
    IdentityEntity toIdentityEntity(SignupRequest request, Boolean verified);

    @Mapping(target = "userId", source = "userId")
    @Mapping(target = "passwordHash", source = "encodedPassword")
    @Mapping(target = "hashAlgo", source = "hashAlgo")
    LocalCredentialsEntity toLocalCredentialsEntity(UUID userId, String encodedPassword, String hashAlgo);
}

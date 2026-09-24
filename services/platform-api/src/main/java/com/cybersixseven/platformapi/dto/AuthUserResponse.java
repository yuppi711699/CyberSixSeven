package com.cybersixseven.platformapi.dto;

import java.util.UUID;

public record AuthUserResponse(UUID id, String email, String nickname, String role) {}

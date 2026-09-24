package com.cybersixseven.platformapi.dto;

import java.util.UUID;

public record ResendCommandResponse(UUID commandId, boolean accepted, String message) {}

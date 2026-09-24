package com.cybersixseven.platformapi.dto;

import java.util.UUID;

public record LeaderboardEntryResponse(UUID userId, String nickname, double score, long rank) {}

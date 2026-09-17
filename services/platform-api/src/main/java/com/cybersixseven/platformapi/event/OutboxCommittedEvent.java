package com.cybersixseven.platformapi.event;

import java.util.UUID;

public record OutboxCommittedEvent(UUID commandId) {}

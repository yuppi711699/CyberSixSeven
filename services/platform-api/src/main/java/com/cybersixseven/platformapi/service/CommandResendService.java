package com.cybersixseven.platformapi.service;

import com.cybersixseven.contracts.device.DeviceCommandResponse;
import com.cybersixseven.platformapi.dto.ResendCommandResponse;
import com.cybersixseven.platformapi.entity.Device;
import com.cybersixseven.platformapi.entity.DeviceCommandEvent;
import com.cybersixseven.platformapi.entity.OutboxEvent;
import com.cybersixseven.platformapi.grpc.DeviceCommandResendClient;
import com.cybersixseven.platformapi.repository.DeviceRepository;
import com.cybersixseven.platformapi.repository.OutboxEventRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CommandResendService {

    private final DeviceRepository deviceRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ResendRateLimiter rateLimiter;
    private final CommandResendAuditService auditService;
    private final DeviceCommandResendClient resendClient;

    public CommandResendService(
            DeviceRepository deviceRepository,
            OutboxEventRepository outboxEventRepository,
            ResendRateLimiter rateLimiter,
            CommandResendAuditService auditService,
            DeviceCommandResendClient resendClient) {
        this.deviceRepository = deviceRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.rateLimiter = rateLimiter;
        this.auditService = auditService;
        this.resendClient = resendClient;
    }

    public ResendCommandResponse resend(UUID staffUserId, UUID deviceId) {
        Device device = deviceRepository
                .findById(deviceId)
                .orElseThrow(() -> new DeviceNotFoundException(deviceId.toString()));
        OutboxEvent outbox = outboxEventRepository
                .findLatestForHardwareId(device.getHardwareId())
                .orElseThrow(CommandNotFoundException::new);
        DeviceCommandEvent command = outbox.getPayload();
        if (!rateLimiter.tryAcquire(staffUserId, device.getId())) {
            auditService.record(
                    staffUserId, device.getId(), command.commandId(), CommandResendAuditService.RATE_LIMITED);
            throw new ResendRateLimitedException();
        }
        try {
            DeviceCommandResponse response = resendClient.send(command);
            boolean accepted = response.getAccepted();
            auditService.record(
                    staffUserId,
                    device.getId(),
                    command.commandId(),
                    accepted ? CommandResendAuditService.ACCEPTED : CommandResendAuditService.REJECTED);
            return new ResendCommandResponse(
                    command.commandId(),
                    accepted,
                    accepted ? "command republished" : "command was not accepted");
        } catch (ResendTimeoutException exception) {
            auditService.record(
                    staffUserId, device.getId(), command.commandId(), CommandResendAuditService.TIMEOUT);
            throw exception;
        } catch (ResendUnavailableException exception) {
            auditService.record(
                    staffUserId, device.getId(), command.commandId(), CommandResendAuditService.UNAVAILABLE);
            throw exception;
        }
    }
}

package com.cybersixseven.platformapi.service;

import com.cybersixseven.contracts.device.DeviceCommandResponse;
import com.cybersixseven.platformapi.dto.ResendCommandResponse;
import com.cybersixseven.platformapi.entity.Device;
import com.cybersixseven.platformapi.entity.DeviceCommandEvent;
import com.cybersixseven.platformapi.entity.OutboxEvent;
import com.cybersixseven.platformapi.grpc.DeviceCommandResendClient;
import com.cybersixseven.platformapi.observability.Correlation;
import com.cybersixseven.platformapi.observability.PlatformMeters;
import com.cybersixseven.platformapi.repository.DeviceRepository;
import com.cybersixseven.platformapi.repository.OutboxEventRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CommandResendService {

    private static final Logger log = LoggerFactory.getLogger(CommandResendService.class);

    private final DeviceRepository deviceRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ResendRateLimiter rateLimiter;
    private final CommandResendAuditService auditService;
    private final DeviceCommandResendClient resendClient;
    private final PlatformMeters meters;

    public CommandResendService(
            DeviceRepository deviceRepository,
            OutboxEventRepository outboxEventRepository,
            ResendRateLimiter rateLimiter,
            CommandResendAuditService auditService,
            DeviceCommandResendClient resendClient,
            PlatformMeters meters) {
        this.deviceRepository = deviceRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.rateLimiter = rateLimiter;
        this.auditService = auditService;
        this.resendClient = resendClient;
        this.meters = meters;
    }

    public ResendCommandResponse resend(UUID staffUserId, UUID deviceId) {
        Device device = deviceRepository
                .findById(deviceId)
                .orElseThrow(() -> new DeviceNotFoundException(deviceId.toString()));
        OutboxEvent outbox = outboxEventRepository
                .findLatestForHardwareId(device.getHardwareId())
                .orElseThrow(CommandNotFoundException::new);
        DeviceCommandEvent command = outbox.getPayload();
        Correlation.put(command.submissionId(), command.commandId(), device.getHardwareId());
        try {
            if (!rateLimiter.tryAcquire(staffUserId, device.getId())) {
                auditService.record(
                        staffUserId, device.getId(), command.commandId(), CommandResendAuditService.RATE_LIMITED);
                meters.resend(CommandResendAuditService.RATE_LIMITED);
                log.info("resend rate limited");
                throw new ResendRateLimitedException();
            }
            try {
                DeviceCommandResponse response = resendClient.send(command);
                boolean accepted = response.getAccepted();
                String outcome = accepted
                        ? CommandResendAuditService.ACCEPTED
                        : CommandResendAuditService.REJECTED;
                auditService.record(staffUserId, device.getId(), command.commandId(), outcome);
                meters.resend(outcome);
                log.info("resend finished accepted={}", accepted);
                return new ResendCommandResponse(
                        command.commandId(),
                        accepted,
                        accepted ? "command republished" : "command was not accepted");
            } catch (ResendTimeoutException exception) {
                auditService.record(
                        staffUserId, device.getId(), command.commandId(), CommandResendAuditService.TIMEOUT);
                meters.resend(CommandResendAuditService.TIMEOUT);
                throw exception;
            } catch (ResendUnavailableException exception) {
                auditService.record(
                        staffUserId, device.getId(), command.commandId(), CommandResendAuditService.UNAVAILABLE);
                meters.resend(CommandResendAuditService.UNAVAILABLE);
                throw exception;
            }
        } finally {
            Correlation.clear();
        }
    }
}

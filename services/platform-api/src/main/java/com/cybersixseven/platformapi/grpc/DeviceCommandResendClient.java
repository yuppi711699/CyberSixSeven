package com.cybersixseven.platformapi.grpc;

import com.cybersixseven.contracts.device.DeviceCommandGrpc;
import com.cybersixseven.contracts.device.DeviceCommandRequest;
import com.cybersixseven.contracts.device.DeviceCommandResponse;
import com.cybersixseven.platformapi.entity.DeviceCommandEvent;
import com.cybersixseven.platformapi.service.ResendTimeoutException;
import com.cybersixseven.platformapi.service.ResendUnavailableException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DeviceCommandResendClient {

    private static final Logger log = LoggerFactory.getLogger(DeviceCommandResendClient.class);

    private final DeviceCommandGrpc.DeviceCommandBlockingStub stub;
    private final Duration deadline;

    public DeviceCommandResendClient(
            DeviceCommandGrpc.DeviceCommandBlockingStub stub,
            @Value("${app.grpc.device-command.deadline}") Duration deadline) {
        this.stub = stub;
        this.deadline = deadline;
    }

    public DeviceCommandResponse send(DeviceCommandEvent command) {
        try {
            return stub.withDeadlineAfter(deadline.toMillis(), TimeUnit.MILLISECONDS)
                    .sendCommand(DeviceCommandRequest.newBuilder()
                            .setCommandId(command.commandId().toString())
                            .setSubmissionId(command.submissionId().toString())
                            .setDeviceId(command.deviceId())
                            .setEvent(command.event())
                            .setIntensity(command.intensity())
                            .build());
        } catch (StatusRuntimeException exception) {
            Status.Code code = exception.getStatus().getCode();
            log.error("resend grpc failed commandId={} code={}", command.commandId(), code);
            if (code == Status.Code.DEADLINE_EXCEEDED) {
                throw new ResendTimeoutException();
            }
            throw new ResendUnavailableException();
        }
    }
}

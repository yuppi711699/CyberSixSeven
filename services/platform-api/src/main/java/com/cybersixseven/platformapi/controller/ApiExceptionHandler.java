package com.cybersixseven.platformapi.controller;

import com.cybersixseven.platformapi.dto.ApiErrorResponse;
import com.cybersixseven.platformapi.service.AccessoryKeyConflictException;
import com.cybersixseven.platformapi.service.DeviceNotFoundException;
import com.cybersixseven.platformapi.service.DeviceNotProvisionedException;
import com.cybersixseven.platformapi.service.InvalidHeartbeatException;
import com.cybersixseven.platformapi.service.InvalidSubmissionException;
import com.cybersixseven.platformapi.service.RobotNotFoundException;
import com.cybersixseven.platformapi.service.SubmissionNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InvalidSubmissionException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleInvalidSubmission(InvalidSubmissionException exception) {
        return new ApiErrorResponse("INVALID_SUBMISSION", exception.getMessage());
    }

    @ExceptionHandler(SubmissionNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiErrorResponse handleSubmissionNotFound() {
        return new ApiErrorResponse("SUBMISSION_NOT_FOUND", "submission not found");
    }

    @ExceptionHandler(AccessoryKeyConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiErrorResponse handleAccessoryKeyConflict(AccessoryKeyConflictException exception) {
        return new ApiErrorResponse("ACCESSORY_KEY_CONFLICT", exception.getMessage());
    }

    @ExceptionHandler(InvalidHeartbeatException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleInvalidHeartbeat(InvalidHeartbeatException exception) {
        return new ApiErrorResponse("INVALID_HEARTBEAT", exception.getMessage());
    }

    @ExceptionHandler(DeviceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiErrorResponse handleDeviceNotFound(DeviceNotFoundException exception) {
        return new ApiErrorResponse("DEVICE_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(DeviceNotProvisionedException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiErrorResponse handleDeviceNotProvisioned(DeviceNotProvisionedException exception) {
        return new ApiErrorResponse("DEVICE_NOT_PROVISIONED", exception.getMessage());
    }

    @ExceptionHandler(RobotNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiErrorResponse handleRobotNotFound(RobotNotFoundException exception) {
        return new ApiErrorResponse("ROBOT_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleMalformedJson() {
        return new ApiErrorResponse(
                "MALFORMED_REQUEST", "request body must contain only well-formed answers");
    }
}

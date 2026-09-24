package com.cybersixseven.platformapi.controller;

import com.cybersixseven.platformapi.dto.ApiErrorResponse;
import com.cybersixseven.platformapi.service.AccessoryKeyConflictException;
import com.cybersixseven.platformapi.service.AccessoryNotReadyException;
import com.cybersixseven.platformapi.service.AuthUnauthorizedException;
import com.cybersixseven.platformapi.service.DeviceNotFoundException;
import com.cybersixseven.platformapi.service.DeviceNotProvisionedException;
import com.cybersixseven.platformapi.service.DuplicateEmailException;
import com.cybersixseven.platformapi.service.InvalidAuthException;
import com.cybersixseven.platformapi.service.InvalidHeartbeatException;
import com.cybersixseven.platformapi.service.InvalidSubmissionException;
import com.cybersixseven.platformapi.service.CommandNotFoundException;
import com.cybersixseven.platformapi.service.DisplayOrderConflictException;
import com.cybersixseven.platformapi.service.InvalidQuestionException;
import com.cybersixseven.platformapi.service.ProductUnavailableException;
import com.cybersixseven.platformapi.service.QuestionNotFoundException;
import com.cybersixseven.platformapi.service.ResendRateLimitedException;
import com.cybersixseven.platformapi.service.ResendTimeoutException;
import com.cybersixseven.platformapi.service.ResendUnavailableException;
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

    @ExceptionHandler(InvalidAuthException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleInvalidAuth(InvalidAuthException exception) {
        return new ApiErrorResponse("INVALID_AUTH", exception.getMessage());
    }

    @ExceptionHandler(DuplicateEmailException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiErrorResponse handleDuplicateEmail() {
        return new ApiErrorResponse("EMAIL_EXISTS", "email already registered");
    }

    @ExceptionHandler(AuthUnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiErrorResponse handleAuthUnauthorized(AuthUnauthorizedException exception) {
        return new ApiErrorResponse(exception.getCode(), exception.getMessage());
    }

    @ExceptionHandler(ProductUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiErrorResponse handleProductUnavailable(ProductUnavailableException exception) {
        return new ApiErrorResponse("PRODUCT_UNAVAILABLE", exception.getMessage());
    }

    @ExceptionHandler(AccessoryNotReadyException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiErrorResponse handleAccessoryNotReady() {
        return new ApiErrorResponse("ACCESSORY_NOT_READY", "accessory is not ready");
    }

    @ExceptionHandler(InvalidQuestionException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleInvalidQuestion(InvalidQuestionException exception) {
        return new ApiErrorResponse("INVALID_QUESTION", exception.getMessage());
    }

    @ExceptionHandler(QuestionNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiErrorResponse handleQuestionNotFound() {
        return new ApiErrorResponse("QUESTION_NOT_FOUND", "question not found");
    }

    @ExceptionHandler(DisplayOrderConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiErrorResponse handleDisplayOrderConflict() {
        return new ApiErrorResponse("DISPLAY_ORDER_CONFLICT", "displayOrder is already used");
    }

    @ExceptionHandler(CommandNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiErrorResponse handleCommandNotFound() {
        return new ApiErrorResponse("COMMAND_NOT_FOUND", "command not found");
    }

    @ExceptionHandler(ResendRateLimitedException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public ApiErrorResponse handleResendRateLimited() {
        return new ApiErrorResponse("RESEND_RATE_LIMITED", "resend rate limit exceeded");
    }

    @ExceptionHandler(ResendTimeoutException.class)
    @ResponseStatus(HttpStatus.GATEWAY_TIMEOUT)
    public ApiErrorResponse handleResendTimeout() {
        return new ApiErrorResponse("RESEND_TIMEOUT", "command resend timed out");
    }

    @ExceptionHandler(ResendUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiErrorResponse handleResendUnavailable() {
        return new ApiErrorResponse("RESEND_UNAVAILABLE", "command service unavailable");
    }
}

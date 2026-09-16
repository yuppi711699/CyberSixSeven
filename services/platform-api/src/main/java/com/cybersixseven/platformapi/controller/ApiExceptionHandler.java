package com.cybersixseven.platformapi.controller;

import com.cybersixseven.platformapi.dto.ApiErrorResponse;
import com.cybersixseven.platformapi.service.InvalidSubmissionException;
import com.cybersixseven.platformapi.service.RobotNotFoundException;
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

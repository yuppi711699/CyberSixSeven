package com.cybersixseven.platformapi.service;

public class DeviceNotProvisionedException extends RuntimeException {

    public DeviceNotProvisionedException(String message) {
        super(message);
    }
}

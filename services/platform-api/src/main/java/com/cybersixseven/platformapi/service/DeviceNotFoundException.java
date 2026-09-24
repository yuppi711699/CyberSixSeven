package com.cybersixseven.platformapi.service;

public class DeviceNotFoundException extends RuntimeException {

    public DeviceNotFoundException(String hardwareId) {
        super("device not found: " + hardwareId);
    }
}

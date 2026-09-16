package com.neurosys.backend.enums;

public enum DeploymentTargetStatus {
    PENDING,
    DOWNLOADING,
    INSTALLING,
    INSTALLED,
    ALREADY_INSTALLED,
    FAILED,
    CANCELLED,
    OFFLINE
}

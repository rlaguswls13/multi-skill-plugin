package com.msp.inbox;

/**
 * Processing status of an event in the transactional inbox.
 */
public enum InboxStatus {
    PENDING,
    PROCESSED,
    FAILED
}

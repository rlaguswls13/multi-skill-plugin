package com.msp.cgroups;

/**
 * Detection version of Linux cgroups or host OS fallback.
 */
public enum CgroupVersion {
    V1,
    V2,
    HOST_FALLBACK
}

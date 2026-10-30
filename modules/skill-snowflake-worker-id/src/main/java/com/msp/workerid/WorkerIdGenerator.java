package com.msp.workerid;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.security.SecureRandom;
import java.util.Enumeration;
import java.util.Objects;

/**
 * 분산 환경에서 외부 코디네이터(Redis/ZooKeeper) 없이
 * 호스트 IP, MAC 주소, PID 비트패킹을 활용하여 유일한 Worker ID를 자동 할당하는 생성기.
 */
public final class WorkerIdGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private WorkerIdGenerator() {}

    /**
     * 호스트 IP, PID, MAC 주소를 순차적으로 조합하여 0부터 maxWorkerId 사이의 고유 Worker ID를 자동 프로비저닝합니다.
     *
     * @param maxWorkerId 허용되는 최대 Worker ID (예: Snowflake 10비트인 경우 1023)
     * @return 0 ~ maxWorkerId 범위의 정수
     */
    public static int autoAssign(int maxWorkerId) {
        if (maxWorkerId <= 0) {
            throw new IllegalArgumentException("maxWorkerId must be positive, got: " + maxWorkerId);
        }

        try {
            return fromPidAndIp(maxWorkerId);
        } catch (Exception e1) {
            try {
                return fromMacAddress(maxWorkerId);
            } catch (Exception e2) {
                try {
                    return fromHostIp(maxWorkerId);
                } catch (Exception e3) {
                    return RANDOM.nextInt(maxWorkerId + 1);
                }
            }
        }
    }

    /**
     * 현재 프로세스 ID(PID)와 로컬 호스트 IP의 마지막 옥텟을 비트패킹하여 Worker ID를 생성합니다.
     */
    public static int fromPidAndIp(int maxWorkerId) {
        long pid = ProcessHandle.current().pid();
        byte[] ipBytes = getLocalIpBytes();
        long ipHash = 0;
        if (ipBytes != null && ipBytes.length > 0) {
            for (byte b : ipBytes) {
                ipHash = (ipHash << 8) | (b & 0xFF);
            }
        } else {
            ipHash = System.identityHashCode(System.getenv());
        }

        long combined = ((pid & 0xFFFF) << 16) ^ (ipHash & 0xFFFF);
        return (int) Math.abs(combined % (maxWorkerId + 1));
    }

    /**
     * 네트워크 인터페이스의 하드웨어 MAC 주소 바이트 해시를 기반으로 Worker ID를 생성합니다.
     */
    public static int fromMacAddress(int maxWorkerId) {
        byte[] mac = getMacAddressBytes();
        if (mac == null || mac.length == 0) {
            throw new IllegalStateException("No valid MAC address found on network interfaces");
        }
        long hash = 0;
        for (byte b : mac) {
            hash = (hash * 31) + (b & 0xFF);
        }
        return (int) Math.abs(hash % (maxWorkerId + 1));
    }

    /**
     * 로컬 호스트 IPv4 주소의 최하위 바이트를 기반으로 Worker ID를 생성합니다.
     */
    public static int fromHostIp(int maxWorkerId) {
        byte[] ip = getLocalIpBytes();
        if (ip == null || ip.length == 0) {
            throw new IllegalStateException("Local host IP could not be resolved");
        }
        int lastOctet = ip[ip.length - 1] & 0xFF;
        return lastOctet % (maxWorkerId + 1);
    }

    private static byte[] getLocalIpBytes() {
        try {
            InetAddress localHost = InetAddress.getLocalHost();
            return localHost.getAddress();
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] getMacAddressBytes() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || ni.isVirtual() || !ni.isUp()) {
                    continue;
                }
                byte[] mac = ni.getHardwareAddress();
                if (mac != null && mac.length > 0) {
                    return mac;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}

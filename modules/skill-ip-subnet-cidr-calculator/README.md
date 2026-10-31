# 🌐 IP Subnet CIDR Calculator (`skill-ip-subnet-cidr-calculator`)

High-performance, zero-dependency IPv4 CIDR calculator and subnet analyzer written in pure Java 21 using bitwise arithmetic.

---

## ✨ Features

- **Zero Runtime Dependencies**: Completely built on pure Java 21 without external networking or math libraries.
- **Full Subnet Details**: Calculates network address, broadcast address, netmask, usable host range (first/last), total hosts, and usable hosts.
- **Edge Prefix Support**:
  - `/32`: Single host route (1 host, 1 usable).
  - `/31`: Point-to-point links (RFC 3021, 2 usable hosts).
  - `/0`: Full IPv4 address space (4,294,967,294 usable hosts).
- **Fast IP Membership**: Instant bitwise checking whether an IP belongs to a CIDR block (`contains`).
- **Subnet Overlap Detection**: Instant interval intersection testing whether two subnets overlap (`overlaps`).

---

## 🚀 Quick Start

### 1. Calculate Subnet Metadata

```java
import com.msp.cidr.CidrCalculator;
import com.msp.cidr.SubnetInfo;

SubnetInfo subnet = CidrCalculator.calculate("192.168.1.50/24");

System.out.println("Network:   " + subnet.networkAddress());   // 192.168.1.0
System.out.println("Broadcast: " + subnet.broadcastAddress()); // 192.168.1.255
System.out.println("Netmask:   " + subnet.netmask());          // 255.255.255.0
System.out.println("First Host:" + subnet.firstHost());        // 192.168.1.1
System.out.println("Last Host: " + subnet.lastHost());         // 192.168.1.254
System.out.println("Usable:    " + subnet.usableHosts());      // 254
```

### 2. IP Containment and Subnet Overlaps

```java
import com.msp.cidr.CidrCalculator;

// Check if IP is within subnet
boolean allowed = CidrCalculator.contains("10.0.0.0/8", "10.42.12.99"); // true

// Check if two subnets conflict or overlap
boolean conflict = CidrCalculator.overlaps("192.168.1.0/24", "192.168.0.0/16"); // true
```

---

## 🛠️ Testing

```bash
./gradlew :modules:skill-ip-subnet-cidr-calculator:test
```

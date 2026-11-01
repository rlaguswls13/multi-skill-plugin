# RDAP Client Lite & WHOIS Formatter (`skill-ip-whois-rdap-client-lite`)

Zero-dependency Java 21 library for building IANA RDAP (Registration Data Access Protocol, RFC 7480/7482/7483) queries, parsing RDAP JSON responses, and rendering human-readable WHOIS text reports.

## Features

- **Query URL Builder**: Construct standardized REST endpoints for IP addresses (`/ip/192.0.2.1`), CIDR blocks, AS numbers (`/autnum/15169`), and domains across regional internet registries (ARIN, RIPE, APNIC, LACNIC, AFRINIC).
- **Embedded Lightweight JSON Parser**: Parses complex nested RDAP JSON responses without any 3rd-party dependencies (no Jackson, Gson, or FastJson).
- **vCard Extractor**: Automatically parses RFC 7483 `vcardArray` structures to extract contact names (`fn`), organizations (`org`), emails, telephone numbers, and postal addresses.
- **Classic WHOIS Formatter**: Renders structured RDAP data into traditional, clean WHOIS terminal text format.

## Runnable Code Example

```java
import com.msp.rdap.RdapNetworkRecord;
import com.msp.rdap.RdapParser;
import com.msp.rdap.RdapQueryBuilder;
import com.msp.rdap.WhoisFormatter;
import java.net.URI;

public class RdapExample {
    public static void main(String[] args) {
        // 1. Build Query URL
        RdapQueryBuilder builder = RdapQueryBuilder.create();
        URI queryUri = builder.buildIpQuery("192.0.2.1");
        System.out.println("Query URI: " + queryUri);

        // 2. Parse RDAP JSON string
        String rdapJson = """
        {
          "handle": "NET-192-0-2-0-1",
          "startAddress": "192.0.2.0",
          "endAddress": "192.0.2.255",
          "ipVersion": "v4",
          "name": "TEST-NET-1",
          "country": "US",
          "status": ["active"]
        }
        """;

        RdapNetworkRecord net = RdapParser.parseNetwork(rdapJson);
        System.out.println("Network Name: " + net.name());
        System.out.println("Range: " + net.startAddress() + " - " + net.endAddress());

        // 3. Format into classic WHOIS view
        String whoisText = WhoisFormatter.formatNetwork(net);
        System.out.println(whoisText);
    }
}
```

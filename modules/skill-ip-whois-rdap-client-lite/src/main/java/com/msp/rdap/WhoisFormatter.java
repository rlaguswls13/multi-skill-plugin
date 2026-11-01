package com.msp.rdap;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Formats RDAP records into classic human-readable WHOIS text representation.
 */
public final class WhoisFormatter {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")
            .withZone(ZoneOffset.UTC);

    private WhoisFormatter() {
    }

    /**
     * Formats an {@link RdapNetworkRecord} into classic WHOIS style text.
     */
    public static String formatNetwork(RdapNetworkRecord net) {
        if (net == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        appendField(sb, "NetRange", net.startAddress() != null && net.endAddress() != null ?
                net.startAddress() + " - " + net.endAddress() : null);
        appendField(sb, "NetName", net.name());
        appendField(sb, "NetHandle", net.handle());
        appendField(sb, "NetType", net.type());
        appendField(sb, "IPVersion", net.ipVersion());
        appendField(sb, "Country", net.country());
        if (!net.status().isEmpty()) {
            appendField(sb, "Status", String.join(", ", net.status()));
        }
        appendField(sb, "Port43", net.port43());

        // Events
        for (Map.Entry<String, java.time.Instant> entry : net.events().entrySet()) {
            appendField(sb, "Event (" + entry.getKey() + ")", DATE_FMT.format(entry.getValue()));
        }

        // Entities
        for (RdapEntityRecord entity : net.entities()) {
            sb.append("\n# Entity: ").append(entity.handle() != null ? entity.handle() : "Unnamed").append("\n");
            if (!entity.roles().isEmpty()) {
                appendField(sb, "Roles", String.join(", ", entity.roles()));
            }
            appendField(sb, "OrgName", entity.organization());
            appendField(sb, "ContactName", entity.formattedName());
            appendField(sb, "Email", entity.email());
            appendField(sb, "Phone", entity.phone());
            appendField(sb, "Address", entity.address());
        }

        return sb.toString();
    }

    /**
     * Formats an {@link RdapAutnumRecord} into classic WHOIS style text.
     */
    public static String formatAutnum(RdapAutnumRecord as) {
        if (as == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        appendField(sb, "ASNumber", as.startAutnum() == as.endAutnum() ?
                String.valueOf(as.startAutnum()) : as.startAutnum() + " - " + as.endAutnum());
        appendField(sb, "ASName", as.name());
        appendField(sb, "ASHandle", as.handle());
        appendField(sb, "Country", as.country());
        if (!as.status().isEmpty()) {
            appendField(sb, "Status", String.join(", ", as.status()));
        }
        appendField(sb, "Port43", as.port43());

        // Events
        for (Map.Entry<String, java.time.Instant> entry : as.events().entrySet()) {
            appendField(sb, "Event (" + entry.getKey() + ")", DATE_FMT.format(entry.getValue()));
        }

        // Entities
        for (RdapEntityRecord entity : as.entities()) {
            sb.append("\n# Entity: ").append(entity.handle() != null ? entity.handle() : "Unnamed").append("\n");
            if (!entity.roles().isEmpty()) {
                appendField(sb, "Roles", String.join(", ", entity.roles()));
            }
            appendField(sb, "OrgName", entity.organization());
            appendField(sb, "ContactName", entity.formattedName());
            appendField(sb, "Email", entity.email());
        }

        return sb.toString();
    }

    private static void appendField(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(String.format("%-18s: %s\n", label, value.trim()));
        }
    }
}

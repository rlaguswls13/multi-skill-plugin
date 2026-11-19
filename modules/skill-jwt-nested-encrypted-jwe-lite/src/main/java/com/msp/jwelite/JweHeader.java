package com.msp.jwelite;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

/**
 * RFC 7516 JWE Protected Header.
 */
public record JweHeader(
        String alg,
        String enc,
        String cty,
        String kid,
        String typ
) {

    public JweHeader {
        Objects.requireNonNull(alg, "alg must not be null");
        Objects.requireNonNull(enc, "enc must not be null");
    }

    public static Builder builder() {
        return new Builder();
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"alg\":\"").append(alg).append("\",");
        sb.append("\"enc\":\"").append(enc).append("\"");
        if (cty != null) {
            sb.append(",\"cty\":\"").append(cty).append("\"");
        }
        if (kid != null) {
            sb.append(",\"kid\":\"").append(kid).append("\"");
        }
        if (typ != null) {
            sb.append(",\"typ\":\"").append(typ).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    public String toBase64Url() {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(toJson().getBytes(StandardCharsets.UTF_8));
    }

    public static JweHeader fromBase64Url(String base64Url) {
        byte[] bytes = Base64.getUrlDecoder().decode(base64Url);
        String json = new String(bytes, StandardCharsets.UTF_8);
        return fromJson(json);
    }

    public static JweHeader fromJson(String json) {
        String clean = json.trim();
        if (!clean.startsWith("{") || !clean.endsWith("}")) {
            throw new JweException("Invalid JSON object for JWE header: " + json);
        }
        String body = clean.substring(1, clean.length() - 1);
        String[] pairs = body.split(",");
        String alg = null;
        String enc = null;
        String cty = null;
        String kid = null;
        String typ = null;

        for (String pair : pairs) {
            String[] kv = pair.split(":", 2);
            if (kv.length != 2) continue;
            String key = unquote(kv[0].trim());
            String val = unquote(kv[1].trim());
            switch (key) {
                case "alg" -> alg = val;
                case "enc" -> enc = val;
                case "cty" -> cty = val;
                case "kid" -> kid = val;
                case "typ" -> typ = val;
            }
        }

        if (alg == null || enc == null) {
            throw new JweException("JWE header missing required 'alg' or 'enc' parameters: " + json);
        }
        return new JweHeader(alg, enc, cty, kid, typ);
    }

    private static String unquote(String s) {
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    public static final class Builder {
        private String alg = JweAlgorithm.RSA_OAEP_256;
        private String enc = JweEncryption.A256GCM;
        private String cty;
        private String kid;
        private String typ;

        public Builder alg(String alg) {
            this.alg = alg;
            return this;
        }

        public Builder enc(String enc) {
            this.enc = enc;
            return this;
        }

        public Builder cty(String cty) {
            this.cty = cty;
            return this;
        }

        public Builder kid(String kid) {
            this.kid = kid;
            return this;
        }

        public Builder typ(String typ) {
            this.typ = typ;
            return this;
        }

        public JweHeader build() {
            return new JweHeader(alg, enc, cty, kid, typ);
        }
    }
}

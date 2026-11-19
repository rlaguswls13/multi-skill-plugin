# Changelog

All notable changes to `multi-skill-plugin` are documented in this file.

## [v2.0.0] - 2026-11-19

### Highlights
- **332 Single-Skill Modules** (`modules/*`): Expanded from 102 modules (`v1.0.0`) by **+230 new zero-dependency Java 21 modules** across Sprint #6 through Sprint #28.
- **8 Domain Starter Bundles** (`starters/*`): Added 4 new domain starter bundles on top of the original 4 starters (total **340 Standalone JARs**).
- **100% Cross-Platform CI Green**: Verified on Windows and Linux (`ubuntu-latest`, JDK 21 Temurin) via GitHub Actions.

### Added — 2nd-Wave Domain Starter Bundles (`starters/*`)
- `starter-http-network` (`HttpNetworkKit`): RFC 7807 Problem Details, CORS Preflight, ETag Conditional Evaluator, RFC 8288 Link Header, IP Bogon/SSRF Classifier, CIDR Subnet Calculator.
- `starter-sql-data` (`SqlDataKit`): SQL Builder Lite, Identifier Sanitizer, SQL Injection Detector, Keyset Seek Pagination, Sharding Key Router, JSON Path Predicate Builder.
- `starter-probabilistic-algo` (`ProbabilisticAlgoKit`): Bloom Filter, HyperLogLog++, T-Digest Quantile Estimator, Count-Min Sketch, Roaring Bitmap, Consistent Hashing.
- `starter-string-codec` (`StringCodecKit`): Aho-Corasick Multi-Pattern, KMP Automaton, Suffix Array & LCP, VarInt/LEB128 Codec, z-base-32 Codec, BER-TLV/ASN.1 Stream Codec.

### Added — +230 Single-Skill Modules (Sprint #6 ~ #28)
- **Security & Auth**: JWK/JWKS/Thumbprint (RFC 7638), JWE (RFC 7516), JWS Detached (RFC 7515), PASETO v3.local, DPoP, COSE Key, X.509/CRL/OCSP (RFC 6960), TOTP (RFC 6238), NIST Password Entropy, K-Anonymity, `@Mask`.
- **HTTP, IP & Geo**: RFC 7807 Problem Details, RFC 8941 Structured Fields, RFC 8288 Link Header, RFC 7616 Digest Auth, RFC 6265 CookieJar, CORS, HPACK, SSE Client/Emitter, CIDR Radix Trie LPM, BGP Prefix Aggregator, Bogon/SSRF Guard, DNS Wire Parser, Geohash, QuadKey, Encoded Polyline, Polygon Geofence.
- **Concurrency & Resilience**: GCRA / Sliding Log / Sliding Counter / Dual Leaky-Token / Distributed Redis Lua / TCP Vegas Adaptive Rate Limiters, Hedged Request Executor, Bulkhead Gate, Retry DLQ, Striped ReadWriteLock, Event Ring Sequencer.
- **Probabilistic & Sketching**: Counting/Scalable/Partitioned/Cuckoo/Quotient Bloom Filters, HyperLogLog++, T-Digest, Count-Min Sketch, SimHash, MinHash-LSH, Roaring Bitmap, Ketama Consistent Hash Ring.
- **SQL & Data**: SQL Lexer/Clause Splitter/AST Visitor, Named Parameter Binder, Identifier Sanitizer, Injection Detector, Window/CTE/Upsert/JSON Path Builders, Keyset Seek Paginator, Sharding Router, DDL Generator, POI-Free `.xlsx` Reader.
- **String, Codec & Process**: Aho-Corasick, KMP, Boyer-Moore-Horspool, Rabin-Karp, Suffix Array/LCP, BM25, Inverted Index, ReDoS Guard, VarInt/LEB128, MsgPack, CBOR, BER-TLV, Bencode, Base91/62/58/32/z-base-32, PTY Emulator, cgroups Monitor, PID Lockfile Guard, Resource Quota Watchdog, POSIX/Windows CLI Tokenizer.

### Fixed — Cross-Platform Determinism & CI Stability
- Sorted `Files.walkFileTree` paths in `skill-file-content-searcher` for deterministic traversal on Linux `ext4`.
- Switched Base64 tamper assertions in `skill-password-hasher` and `skill-jwt-lite` to mutate the first character (avoiding 2-bit trailing padding collisions).
- Added OS-aware command selection (`cmd.exe /c` vs `sh -c`) in `skill-process-std-stream-multiplexer`.
- Fixed race condition in `skill-priority-queue-flow` (`PriorityWorkQueueTest`) by awaiting `WorkerPool` termination before asserting `processedCount`.

---

## [v1.0.0] - 2026-10-30
- Initial public release with 102 single-skill modules (`modules/*`), 4 domain starter bundles (`starters/*`), JitPack integration, and GitHub Actions CI.

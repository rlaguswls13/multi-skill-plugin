# 📻 Skill: Morse Code Codec (`com.msp.morse`)

Bidirectional Morse code encoder and decoder supporting International Morse Code (Latin alphabet A-Z, numbers 0-9, standard punctuation) and Korean Telegraphic Morse Code (국문 전신 부호) with automatic syllable composition. Pure Java 21 standard library with zero runtime dependencies.

---

## 🌟 Key Features

- **International Morse Standard**: Complete ITU standard support for letters, numbers, and common punctuation.
- **Korean Hangul Decomposition & Assembly**: Automatically separates Hangul syllables into elementary Jamo, encodes them, and reassembles them upon decoding.
- **Word & Character Separators**: Clear spacing conventions (`" "` between letters/jamo, `" / "` or triple spaces between words).
- **Dual Mode Decoder**: Unified decoder supporting `MorseMode.INTERNATIONAL` and `MorseMode.KOREAN`.

---

## 🚀 Quick Start

### 1. English & International Distress Signals

```java
import com.msp.morse.MorseCodec;

// SOS signal
String sosMorse = MorseCodec.encode("SOS");
// Output: "... --- ..."

String sosText = MorseCodec.decode("... --- ...");
// Output: "SOS"

// Words with spaces
String hello = MorseCodec.encode("HELLO WORLD");
// Output: ".... . .-.. .-.. --- / .-- --- .-. .-.. -.."
```

### 2. Korean Hangul Telegraph Morse

```java
import com.msp.morse.MorseCodec;

// Encode Korean words
String morse = MorseCodec.encode("한글 2026");
// Morse encoded telegraph sequence

// Decode back with automatic Hangul syllable assembly
String hangul = MorseCodec.decodeHangul(morse);
// Output: "한글 2026"
```

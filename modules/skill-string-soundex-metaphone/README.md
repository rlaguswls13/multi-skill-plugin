# Soundex & Metaphone Phonetic Codec (`skill-string-soundex-metaphone`)

Zero-dependency phonetic matching and sound-alike string codec implementing **Soundex** and **Metaphone** algorithms in pure Java 21.

---

## 🚀 Key Features

- **Zero Runtime Dependencies**: Pure Java 21 standard library.
- **US Census Soundex**: Standard 4-character phonetic hashing (`S530`) with adjacent letter and H/W separation handling.
- **Lawrence Philips Metaphone**: Variable-length phonetic encoding resolving English silent consonants, diphthongs, and digraphs (`Knight` -> `NT`).
- **Phonetic Equivalence Checks**: Fast helper methods `isSoundexEqual(s1, s2)` and `isMetaphoneEqual(s1, s2)`.

---

## 📦 Usage Examples

### 1. Soundex Matching

```java
import com.msp.phonetic.PhoneticCodec;

// Compute Soundex codes
String c1 = PhoneticCodec.soundex("Smith");   // "S530"
String c2 = PhoneticCodec.soundex("Smythe");  // "S530"
String c3 = PhoneticCodec.soundex("Pfister"); // "P236"

// Soundex phonetic equality
boolean match = PhoneticCodec.isSoundexEqual("Robert", "Rupert"); // true ("R163")
```

### 2. Metaphone Matching

```java
// Handles silent consonants and phonetics
String m1 = PhoneticCodec.metaphone("Knight"); // "NT"
String m2 = PhoneticCodec.metaphone("Night");  // "NT"

boolean sameSound = PhoneticCodec.isMetaphoneEqual("Knight", "Night"); // true

String phone = PhoneticCodec.metaphone("Phone"); // "FN"
String edge = PhoneticCodec.metaphone("Edge");   // "EJ"
```

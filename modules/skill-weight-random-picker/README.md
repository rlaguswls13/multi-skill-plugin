# 🎲 skill-weight-random-picker

> **Weighted random item picker for A/B testing, lotteries, and drop rates**  
> Pure Java 21 zero-dependency library providing $O(\log N)$ cumulative distribution function (CDF) binary search selection and mathematically rigorous weighted selection without replacement (Efraimidis-Spirakis A-Res algorithm).

---

## 🚀 Key Features

- **Zero Runtime Dependencies**: Pure Java 21 standard library.
- **O(log N) Performance**: Binary search on precomputed cumulative weight array.
- **Floating-Point Weights**: Supports arbitrary positive double weights (e.g. `0.005`, `99.9`).
- **Flexible Picking**:
  - `pick()`: Single weighted random selection.
  - `pick(count)`: Multi-sample with replacement (independent trials).
  - `pickWithoutReplacement(count)`: Multi-sample without replacement (no duplicates, strictly respecting weights).
- **Thread-Safe**: Fully safe for concurrent sampling across threads.

---

## 📦 Gradle Dependency

```groovy
dependencies {
    implementation 'com.msp:skill-weight-random-picker:1.0.0'
}
```

---

## 💡 Usage Examples

### 1. A/B Testing Traffic Split

```java
import com.msp.random.WeightedRandomPicker;

WeightedRandomPicker<String> trafficRouter = WeightedRandomPicker.<String>builder()
        .add("variant-control", 70.0) // 70% traffic
        .add("variant-exp-v1",  20.0) // 20% traffic
        .add("variant-exp-v2",  10.0) // 10% traffic
        .build();

// Route incoming user request
String assignedVariant = trafficRouter.pick();
System.out.println("User routed to: " + assignedVariant);
```

### 2. Game Loot Drop Rates

```java
record ItemDrop(String name, String rarity) {}

WeightedRandomPicker<ItemDrop> lootBox = WeightedRandomPicker.<ItemDrop>builder()
        .add(new ItemDrop("Common Sword", "COMMON"), 85.0)
        .add(new ItemDrop("Rare Shield", "RARE"),   12.5)
        .add(new ItemDrop("Epic Bow", "EPIC"),       2.4)
        .add(new ItemDrop("Legendary Ring", "LEGENDARY"), 0.1)
        .build();

ItemDrop reward = lootBox.pick();
System.out.println("You received: " + reward.name());
```

### 3. Lottery / Raffle Drawing Without Replacement

```java
WeightedRandomPicker<String> raffle = WeightedRandomPicker.<String>builder()
        .add("Alice (5 tickets)", 5.0)
        .add("Bob (2 tickets)", 2.0)
        .add("Charlie (10 tickets)", 10.0)
        .add("David (1 ticket)", 1.0)
        .build();

// Pick 1st, 2nd, and 3rd place winners (unique individuals)
List<String> winners = raffle.pickWithoutReplacement(3);
System.out.println("1st Place: " + winners.get(0));
System.out.println("2nd Place: " + winners.get(1));
System.out.println("3rd Place: " + winners.get(2));
```

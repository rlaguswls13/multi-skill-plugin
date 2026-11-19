# Count-Min Sketch Lite (`skill-sketch-count-min-lite`)

High-performance, zero-dependency Count-Min Sketch probabilistic data structure in Java 21 for frequency estimation, streaming frequency summaries, and Heavy Hitters (Top-K) detection.

## Features

- **Formal Error Bounds**: Configurable relative error tolerance $\epsilon$ and error probability bound $\delta$, automatically determining dimensions ($w = \lceil e / \epsilon \rceil, d = \lceil \ln(1 / \delta) \rceil$).
- **No Underestimation Guarantee**: Count-Min guarantees $\hat{a}_i \ge a_i$, and $\hat{a}_i \le a_i + \epsilon N$ with probability $\ge 1 - \delta$.
- **Conservative Update Optimization**: Dramatically reduces overestimation by only incrementing counters lower than the new estimate.
- **Heavy Hitters & Top-K Detection**: Bounded memory tracking of items exceeding a stream fraction $\phi$ or top-K elements.
- **Lock-Free Concurrency**: Provides `AtomicCountMinSketch` utilizing `AtomicLongArray` per row and `LongAdder` for scalable concurrent processing.
- **Pure Java 21**: Zero 3rd party runtime dependencies.

## Runnable Code Example

```java
import com.msp.countmin.CountMinSketch;
import com.msp.countmin.HeavyHitter;

import java.util.List;

public class CountMinExample {
    public static void main(String[] args) {
        // 1. Create a Count-Min Sketch with heavy hitters tracking (phi = 5% threshold)
        CountMinSketch<String> sketch = CountMinSketch.createWithHeavyHitters(0.001, 0.01, 0.05);

        // 2. Stream data updates
        sketch.update("user_101", 1500); // 15% of 10k
        sketch.update("user_202", 800);  // 8% of 10k
        sketch.update("user_303", 200);  // 2% of 10k

        // Stream background traffic
        for (int i = 0; i < 7500; i++) {
            sketch.update("background_item_" + (i % 500));
        }

        // 3. Query frequency estimates
        System.out.println("user_101 frequency: " + sketch.estimateFrequency("user_101")); // ~1500
        System.out.println("user_202 frequency: " + sketch.estimateFrequency("user_202")); // ~800
        System.out.println("Total stream items: " + sketch.getTotalCount());

        // 4. Retrieve heavy hitters (items with >= 5% of stream)
        List<HeavyHitter<String>> heavyHitters = sketch.getHeavyHitters();
        for (HeavyHitter<String> hh : heavyHitters) {
            System.out.printf("Heavy Hitter: %s, freq: %d, ratio: %.2f%%%n",
                    hh.item(), hh.estimatedFrequency(), hh.frequencyRatio() * 100);
        }

        // 5. Retrieve Top-K
        List<HeavyHitter<String>> top2 = sketch.getTopK(2);
        System.out.println("Top 2: " + top2);
    }
}
```

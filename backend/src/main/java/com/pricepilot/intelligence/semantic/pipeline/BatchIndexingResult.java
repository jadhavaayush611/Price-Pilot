package com.pricepilot.intelligence.semantic.pipeline;

import java.io.Serializable;

/**
 * Result object summarizing the outcome of a batch product indexing operation.
 */
public final class BatchIndexingResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private final int totalProcessed;
    private final int totalIndexed;
    private final int totalSkipped;
    private final int totalFailed;
    private final long durationMs;

    public BatchIndexingResult(
            int totalProcessed,
            int totalIndexed,
            int totalSkipped,
            int totalFailed,
            long durationMs) {
        this.totalProcessed = totalProcessed;
        this.totalIndexed = totalIndexed;
        this.totalSkipped = totalSkipped;
        this.totalFailed = totalFailed;
        this.durationMs = durationMs;
    }

    public int getTotalProcessed() {
        return totalProcessed;
    }

    public int getTotalIndexed() {
        return totalIndexed;
    }

    public int getTotalSkipped() {
        return totalSkipped;
    }

    public int getTotalFailed() {
        return totalFailed;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public boolean isSuccessful() {
        return totalFailed == 0;
    }

    @Override
    public String toString() {
        return "BatchIndexingResult{" +
                "totalProcessed=" + totalProcessed +
                ", totalIndexed=" + totalIndexed +
                ", totalSkipped=" + totalSkipped +
                ", totalFailed=" + totalFailed +
                ", durationMs=" + durationMs +
                '}';
    }
}

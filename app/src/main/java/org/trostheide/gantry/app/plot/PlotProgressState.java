package org.trostheide.gantry.app.plot;

/** Thread-safe, Swing-free progress and timing state for one plot run. */
public final class PlotProgressState {
    private TimeEstimator.PlotEstimate estimate;
    private long plotStartMillis;
    private int done;
    private int total;
    private int layerIndex;
    private int totalLayers;

    public synchronized void setEstimate(TimeEstimator.PlotEstimate estimate) {
        this.estimate = estimate;
    }

    public synchronized TimeEstimator.PlotEstimate estimate() { return estimate; }

    public synchronized void start(int totalLayers, long nowMillis) {
        this.plotStartMillis = nowMillis;
        this.totalLayers = totalLayers;
        done = 0;
        total = 0;
        layerIndex = 0;
    }

    public synchronized void layerStarted() { layerIndex++; }

    public synchronized void update(int done, int total) {
        this.done = done;
        this.total = total;
    }

    public synchronized int percent() { return total > 0 ? done * 100 / total : 0; }
    public synchronized double elapsedSeconds(long nowMillis) {
        return Math.max(0, nowMillis - plotStartMillis) / 1000.0;
    }

    public synchronized String liveText(long nowMillis, int speedPercent) {
        double elapsed = elapsedSeconds(nowMillis);
        StringBuilder text = new StringBuilder();
        if (layerIndex > 0 && totalLayers > 0) {
            text.append("Layer ").append(layerIndex).append('/').append(totalLayers).append("  ·  ");
        }
        text.append("Elapsed: ").append(TimeEstimator.format(elapsed));
        if (done > 0 && total > 0 && elapsed > 0) {
            double remaining = elapsed * (total - done) / (double) done;
            text.append("  ·  ~").append(TimeEstimator.format(remaining)).append(" remaining");
        } else if (estimate != null) {
            text.append(" / Est: ").append(TimeEstimator.format(
                    estimate.totalSeconds() * (100.0 / speedPercent)));
        }
        return text.toString();
    }

    public synchronized void resetProgress() {
        done = 0;
        total = 0;
        layerIndex = 0;
        totalLayers = 0;
    }
}

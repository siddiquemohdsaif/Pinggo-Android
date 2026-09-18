package com.w3n.pinggo.call;

/** Coalesces rapid taps while preserving the last requested setting. UI-thread owned. */
public final class LatestMediaState {
  private boolean desired, applied, pending, inFlight;
  public LatestMediaState(boolean initial) { desired = applied = initial; }
  public boolean toggle() { desired = !desired; return desired; }
  public boolean desired() { return desired; }
  public boolean applied() { return applied; }
  public boolean needsApply() { return !pending && desired != applied; }
  public boolean begin() {
    if (!needsApply()) throw new IllegalStateException("No pending change");
    pending = true;
    return inFlight = desired;
  }
  public void complete(boolean success) {
    if (!pending) throw new IllegalStateException("No operation in flight");
    if (success) applied = inFlight;
    else if (desired == inFlight) desired = applied;
    pending = false;
  }
}

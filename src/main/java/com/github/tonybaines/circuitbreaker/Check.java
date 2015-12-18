package com.github.tonybaines.circuitbreaker;

import java.time.Instant;

@FunctionalInterface
public interface Check {
  Status check();

  public static class Status {
    private final boolean result;
    private final String description;
    private final Instant received;

    public Status(boolean result, String description) {
      this.result = result;
      this.description = description;
      this.received = Instant.now();
    }

    public boolean status() {
      return result;
    }

    public String description() {
      return description;
    }

    @Override
    public String toString() {
      return String.format("[%s@%s] %s",
        result ? "PASS" : "FAIL",
        received.toString(),
        description);
    }
  }
}

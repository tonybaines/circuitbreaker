package com.github.tonybaines.circuitbreaker;

import java.util.concurrent.atomic.AtomicLong;

public class Clock {
  private AtomicLong time = new AtomicLong(0);

  public long getTime() {
    return time.get();
  }

  public long tick() {
    return time.incrementAndGet();
  }
}

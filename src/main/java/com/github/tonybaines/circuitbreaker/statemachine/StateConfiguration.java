package com.github.tonybaines.circuitbreaker.statemachine;

import com.github.tonybaines.circuitbreaker.RequestHandler;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;

public class StateConfiguration<INPUT, OUTPUT> {
  private final Duration openTimeout;
  private final ScheduledExecutorService scheduler;
  private final RequestHandler<INPUT, OUTPUT> openBehaviour;
  private final RequestHandler<INPUT, OUTPUT> closedBehaviour;

  public StateConfiguration(Duration openTimeout, ScheduledExecutorService scheduler, RequestHandler<INPUT, OUTPUT> openBehaviour, RequestHandler<INPUT, OUTPUT> closedBehaviour) {
    this.openTimeout = openTimeout;
    this.scheduler = scheduler;
    this.openBehaviour = openBehaviour;
    this.closedBehaviour = closedBehaviour;
  }

  public Duration getOpenTimeout() {
    return openTimeout;
  }

  public ScheduledExecutorService getScheduler() {
    return scheduler;
  }

  public RequestHandler<INPUT, OUTPUT> getOpenBehaviour() {
    return openBehaviour;
  }

  public RequestHandler<INPUT, OUTPUT> getClosedBehaviour() {
    return closedBehaviour;
  }
}

package com.github.tonybaines.circuitbreaker.statemachine;

import com.github.tonybaines.circuitbreaker.Check;

import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class OpenState<INPUT, OUTPUT> extends AbstractState<INPUT,OUTPUT> {
  private final AtomicBoolean timedOut = new AtomicBoolean(false);

  @Override
  public String name() {
    return "Open";
  }

  public OpenState(StateConfiguration<INPUT, OUTPUT> stateConfiguration) {
    super(stateConfiguration);
    // Schedule a flag to toggle once the timeout has elapsed
    stateConfiguration.getScheduler().schedule(() -> timedOut.set(true),
      stateConfiguration.getOpenTimeout().get(ChronoUnit.SECONDS), TimeUnit.SECONDS);
  }

  @Override
  public State<INPUT, OUTPUT> nextState(Check.Status check) {
    if (timedOut.get()) {
      return check.status()
        ? new ClosedState<>(stateConfiguration)
        : new OpenState<>(stateConfiguration);
    } else {
      return this;
    }
  }

  @Override
  public OUTPUT responseTo(INPUT req) {
    return stateConfiguration.getOpenBehaviour().responseTo(req);
  }
}

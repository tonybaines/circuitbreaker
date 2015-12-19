package com.github.tonybaines.circuitbreaker.statemachine;

import com.github.tonybaines.circuitbreaker.Check;
import com.github.tonybaines.circuitbreaker.RequestHandler;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;

public class ClosedState<INPUT, OUTPUT> extends AbstractState<INPUT, OUTPUT> {

  public ClosedState(StateConfiguration<INPUT, OUTPUT> stateConfiguration) {
    super(stateConfiguration);
  }

  @Override
  public String name() {
    return "Closed";
  }

  @Override
  public State<INPUT, OUTPUT> nextState(Check.Status check) {
    return check.status() ? this : new OpenState<>(stateConfiguration);
  }

  @Override
  public OUTPUT responseTo(INPUT req) {
    return stateConfiguration.getClosedBehaviour().responseTo(req);
  }
}

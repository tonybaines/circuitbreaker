package com.github.tonybaines.circuitbreaker.statemachine;

import com.github.tonybaines.circuitbreaker.Check;
import com.github.tonybaines.circuitbreaker.RequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;

public class ForcedOpenState<INPUT, OUTPUT> extends AbstractState<INPUT, OUTPUT> {
  private static final Logger LOG = LoggerFactory.getLogger(ForcedOpenState.class);

  public ForcedOpenState(StateConfiguration<INPUT, OUTPUT> stateConfiguration) {
    super(stateConfiguration);
    LOG.warn("Forced into 'Open' state");
  }

  @Override
  public String name() {
    return "ForcedOpen";
  }

  @Override
  public OUTPUT responseTo(INPUT req) {
    return stateConfiguration.getOpenBehaviour().responseTo(req);
  }

  @Override
  public State nextState(Check.Status check) {
    return this;
  }
}

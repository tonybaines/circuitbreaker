package com.github.tonybaines.circuitbreaker.statemachine;

import com.github.tonybaines.circuitbreaker.Check;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
  public State<INPUT, OUTPUT> nextState(Check.Status check) {
    return this;
  }
}

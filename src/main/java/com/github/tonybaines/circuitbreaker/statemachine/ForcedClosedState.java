package com.github.tonybaines.circuitbreaker.statemachine;

import com.github.tonybaines.circuitbreaker.Check;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ForcedClosedState<INPUT, OUTPUT> extends ClosedState<INPUT, OUTPUT> {
  private static final Logger LOG = LoggerFactory.getLogger(ForcedClosedState.class);
  public ForcedClosedState(StateConfiguration<INPUT, OUTPUT> stateConfiguration) {
    super(stateConfiguration);
    LOG.warn("Forced into 'Closed' state");
  }

  @Override
  public String name() {
    return "ForcedClosed";
  }

  @Override
  public State<INPUT, OUTPUT> nextState(Check.Status check) {
    return this;
  }
}

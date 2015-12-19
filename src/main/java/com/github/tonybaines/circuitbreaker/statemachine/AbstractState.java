package com.github.tonybaines.circuitbreaker.statemachine;

import com.github.tonybaines.circuitbreaker.Check;

public abstract class AbstractState<INPUT, OUTPUT> implements State<INPUT, OUTPUT> {
  protected final StateConfiguration<INPUT, OUTPUT> stateConfiguration;

  public AbstractState(StateConfiguration<INPUT, OUTPUT> stateConfiguration) {
    this.stateConfiguration = stateConfiguration;
  }

  @Override
  public abstract String name();

  @Override
  public abstract State<INPUT, OUTPUT> nextState(Check.Status check);
}

package com.github.tonybaines.circuitbreaker.statemachine;

import com.github.tonybaines.circuitbreaker.Check;
import com.github.tonybaines.circuitbreaker.RequestHandler;

public interface State<INPUT, OUTPUT> extends RequestHandler<INPUT, OUTPUT> {
  String name();

  State<INPUT, OUTPUT> nextState(Check.Status check);
}

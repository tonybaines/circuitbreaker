package com.github.tonybaines.circuitbreaker

import static com.github.tonybaines.circuitbreaker.Check.Status

class CircuitBreakerFixture {
  protected static Check CHECK_ALWAYS_OK = { new Status(true, "TEST") }
  protected static Check CHECK_ALWAYS_FAILS = { new Status(false, "TEST") }
  protected static Check CHECK_THROWS_EXCEPTION = {throw new RuntimeException("Check threw an exception!")}
  protected static RequestHandler<String, Integer> CLOSED_BEHAVIOUR = { x ->
    CircuitBreakerSpec.log.debug("Running 'Closed' behaviour")
    return x.length()
  }
  protected static RequestHandler<String, Integer> OPEN_BEHAVIOUR = {
    CircuitBreakerSpec.log.debug("Running 'Open' behaviour")
    throw new RuntimeException("Fail fast")
  }

  public static class StubChecker implements Check {
    def passes = true
    @Override
    public Status check() {new Status(passes, "TEST")}
  }
}

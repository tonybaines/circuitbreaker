package com.github.tonybaines.circuitbreaker

class CircuitBreakerFixture {
  protected static Check CHECK_ALWAYS_OK = { new Check.Status(true, "TEST") }
  protected static Check CHECK_ALWAYS_FAILS = { new Check.Status(false, "TEST") }
  protected static Check CHECK_THROWS_EXCEPTION = {throw new RuntimeException("Check threw an exception!")}
  protected static CircuitBreaker.RequestHandler<String, Integer> CLOSED_BEHAVIOUR = { x ->
    CircuitBreakerSpec.log.debug("Running 'Closed' behaviour")
    return x.length()
  }
  protected static CircuitBreaker.RequestHandler<String, Integer> OPEN_BEHAVIOUR = {
    CircuitBreakerSpec.log.debug("Running 'Open' behaviour")
    throw new RuntimeException("Fail fast")
  }
}

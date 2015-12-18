package com.github.tonybaines.circuitbreaker

import spock.lang.Specification
import spock.lang.Unroll

import static com.github.tonybaines.circuitbreaker.CircuitBreakerFixture.*

class CircuitBreakerMBeanSpec extends Specification {

  @Unroll
  def "the MBean exposes the result of the last status check (#pattern)"() {
    given:
    StubScheduledExecutor stubScheduler = new StubScheduledExecutor()

    CircuitBreaker<String, Integer> circuitBreaker = CircuitBreaker.newCircuitBreaker()
      .withScheduler(stubScheduler)
      .check(check)
      .whenOpen(OPEN_BEHAVIOUR)
      .whenClosed(CLOSED_BEHAVIOUR)
      .build()

    when:
    stubScheduler.tick()

    then:
    circuitBreaker.getLastCheckDescription().contains pattern
    circuitBreaker.getCurrentState() == state

    where:
    check              | pattern | state
    CHECK_ALWAYS_OK    | 'PASS'  | 'CLOSED'
    CHECK_ALWAYS_FAILS | 'FAIL'  | 'OPEN'
  }

}
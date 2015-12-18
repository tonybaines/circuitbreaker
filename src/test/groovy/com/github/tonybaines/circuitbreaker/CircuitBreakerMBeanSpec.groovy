package com.github.tonybaines.circuitbreaker

import spock.lang.Ignore
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration

import static com.github.tonybaines.circuitbreaker.CircuitBreakerFixture.*

class CircuitBreakerMBeanSpec extends Specification {

  @Unroll
  def "the MBean exposes the result of the last status check (#pattern)"() {
    given:
    StubScheduledExecutor stubScheduler = new StubScheduledExecutor()

    CircuitBreaker<String, Integer> circuitBreaker = CircuitBreaker.newCircuitBreaker()
      .withScheduler(stubScheduler)
      .check(check, Duration.ofSeconds(1))
      .whenOpen(OPEN_BEHAVIOUR, Duration.ofSeconds(2))
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

  def "a Circuit Breaker can be forced into a permanently-open state"() {
    given:
    StubScheduledExecutor stubScheduler = new StubScheduledExecutor()

    CircuitBreaker<String, Integer> circuitBreaker = CircuitBreaker.newCircuitBreaker()
      .withScheduler(stubScheduler)
      .check(CHECK_ALWAYS_OK, Duration.ofSeconds(1))
      .whenOpen(OPEN_BEHAVIOUR, Duration.ofSeconds(2))
      .whenClosed(CLOSED_BEHAVIOUR)
      .build()

    when:
    stubScheduler.tick() // t=1

    then:
    circuitBreaker.getCurrentState() == 'CLOSED'

    when:
    circuitBreaker.forceOpen()
    stubScheduler.tick(2) // t=3
    circuitBreaker.handle('foo')

    then:
    circuitBreaker.getCurrentState() == 'OPEN'
    thrown(RuntimeException)

    when:
    stubScheduler.tick(10) // t=13
    circuitBreaker.handle('foo')

    then:
    circuitBreaker.getCurrentState() == 'OPEN'
    thrown(RuntimeException)

    when: 'the Circuit Breaker is reset, and the open-timeout has expired'
    circuitBreaker.resetToNormalOperation()
    stubScheduler.tick() // t=14

    then:
    circuitBreaker.getCurrentState() == 'CLOSED'
    circuitBreaker.handle('foo') == 3
  }

}
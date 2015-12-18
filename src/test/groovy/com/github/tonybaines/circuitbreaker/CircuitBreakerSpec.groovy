package com.github.tonybaines.circuitbreaker

import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

class CircuitBreakerSpec extends Specification {
  CircuitBreaker.Check CHECK_ALWAYS_OK = { true }
  CircuitBreaker.Check CHECK_ALWAYS_FAILS = { false }
  CircuitBreaker.Check CHECK_THROWS_EXCEPTION = {throw new RuntimeException("Check threw an exception!")}
  CircuitBreaker.RequestHandler<String, Integer> CLOSED_BEHAVIOUR = { x ->
    CircuitBreaker.log("Running 'Closed' behaviour")
    return x.length()
  }
  CircuitBreaker.RequestHandler<String, Integer> OPEN_BEHAVIOUR = {
    CircuitBreaker.log("Running 'Open' behaviour")
    throw new RuntimeException("Fail fast")
  }

  def "when in the 'Closed' state the Circuit Breaker allows requests"() {
    when:
    CircuitBreaker<String, Integer> circuitBreaker = CircuitBreaker.newCircuitBreaker()
      .check(CHECK_ALWAYS_OK)
      .whenClosed(CLOSED_BEHAVIOUR)
      .build()

    then:
    expectClosedCircuitBehaviourFrom circuitBreaker
  }

  def "when in the 'Open' state the Circuit Breaker allows requests"() {
    when:
    CircuitBreaker<String, Integer> circuitBreaker = CircuitBreaker.newCircuitBreaker()
      .check(CHECK_ALWAYS_FAILS)
      .whenOpen(OPEN_BEHAVIOUR)
      .build()

    then:
    expectOpenCircuitBehaviourFrom circuitBreaker
  }

  def "switching from 'Closed' to 'Open' behaviour"() {
    given:
    StubChecker stubChecker = new StubChecker()
    StubScheduledExecutor stubScheduler = new StubScheduledExecutor()

    CircuitBreaker<String, Integer> circuitBreaker = CircuitBreaker.<String, Integer>newCircuitBreaker()
      .check(stubChecker, Duration.ofSeconds(1))
      .whenClosed(CLOSED_BEHAVIOUR)
      .whenOpen(OPEN_BEHAVIOUR, Duration.ofSeconds(4))
      .withScheduler(stubScheduler)
      .build()

    when: "first started"
    stubScheduler.tick() // t=1
    then: "the check passes and the circuit breaker follows 'Closed' behaviour"
    expectClosedCircuitBehaviourFrom circuitBreaker

    when: "the check is made to fail"
    stubChecker.passes = false
    stubScheduler.tick() // t=2
    then: "the circuit breaker follows 'Open' behaviour"
    expectOpenCircuitBehaviourFrom circuitBreaker

    when: "the check recovers"
    stubChecker.passes = true
    stubScheduler.tick() // t=3
    then: "the result of the check is ignored until the open-timeout has elapsed"
    expectOpenCircuitBehaviourFrom circuitBreaker

    when: "Wait for the open-timeout to complete"
    stubScheduler.tick(3) // t=4,5,6
    then: "we're back to 'Closed' behaviour"
    expectClosedCircuitBehaviourFrom circuitBreaker
  }

  def "when the check still fails after the open-timeout, the 'Open Circuit' behaviour continues"() {
    given:
    StubChecker stubChecker = new StubChecker()
    StubScheduledExecutor stubScheduler = new StubScheduledExecutor()

    CircuitBreaker<String, Integer> circuitBreaker = CircuitBreaker.<String, Integer>newCircuitBreaker()
      .check(stubChecker, Duration.ofSeconds(1))
      .whenClosed(CLOSED_BEHAVIOUR)
      .whenOpen(OPEN_BEHAVIOUR, Duration.ofSeconds(4))
      .withScheduler(stubScheduler)
      .build()

    when:"the check starts off working"
    stubScheduler.tick() // t=1
    then:
    expectClosedCircuitBehaviourFrom circuitBreaker

    when: "the check fails"
    stubChecker.passes = false
    stubScheduler.tick() // t=2
    then:
    expectOpenCircuitBehaviourFrom circuitBreaker

    when: "we wait for the open-timeout to complete"
    stubScheduler.tick(3) // t=3,4,5
    then: "... still failing"
    expectOpenCircuitBehaviourFrom circuitBreaker

    when: "the check recovers"
    stubChecker.passes = true
    stubScheduler.tick(3) // t=6,7,8
    then:
    expectClosedCircuitBehaviourFrom circuitBreaker
  }

  def "when the check throws an exception while 'Closed', continue in 'Closed' mode"() {
    given:
    StubScheduledExecutor stubScheduler = new StubScheduledExecutor()

    CircuitBreaker<String, Integer> circuitBreaker = CircuitBreaker.<String, Integer> newCircuitBreaker()
      .check(CHECK_THROWS_EXCEPTION, Duration.ofSeconds(1))
      .whenClosed(CLOSED_BEHAVIOUR)
      .whenOpen(OPEN_BEHAVIOUR, Duration.ofSeconds(4))
      .withScheduler(stubScheduler)
      .build()

    when:
    stubScheduler.tick() // t=1
    then: "Starts off working"
    expectClosedCircuitBehaviourFrom circuitBreaker

    when:
    stubScheduler.tick() // t=2
    then: "... and continues to work"
    expectClosedCircuitBehaviourFrom circuitBreaker

    when:
    stubScheduler.tick() // t=3
    then: "... and continues to work"
    expectClosedCircuitBehaviourFrom circuitBreaker
  }

  def "when the check throws an exception while 'Open', switch to 'Closed' mode"() {
    given:
    StubScheduledExecutor stubScheduler = new StubScheduledExecutor()

    // First fail, then throw an exception
    CircuitBreaker.Check unstableCheck = new CircuitBreaker.Check() {
      AtomicInteger callCount = new AtomicInteger(0)
      public Boolean check() {
        if (callCount.incrementAndGet() <= 1) return false
        else throw new RuntimeException("Check threw an exception!")
      }
    }

    CircuitBreaker<String, Integer> circuitBreaker = CircuitBreaker.<String, Integer>newCircuitBreaker()
      .check(unstableCheck, Duration.ofSeconds(1))
      .whenClosed(CLOSED_BEHAVIOUR)
      .whenOpen(OPEN_BEHAVIOUR, Duration.ofSeconds(4))
      .withScheduler(stubScheduler)
      .build()

    expect: "Starts off failing"
    expectOpenCircuitBehaviourFrom circuitBreaker

    when: "Wait for the open-timeout to complete"
    stubScheduler.tick(4)

    then:
    expectClosedCircuitBehaviourFrom circuitBreaker

    when:
    stubScheduler.tick() // t=5
    then: "continues to work despite the exceptions"
    expectClosedCircuitBehaviourFrom circuitBreaker
  }


  void expectClosedCircuitBehaviourFrom(CircuitBreaker circuitBreaker) {
    assert circuitBreaker.handle("foo") == 3
  }

  void expectOpenCircuitBehaviourFrom(CircuitBreaker circuitBreaker) {
    try {
      circuitBreaker.handle("foo")
      fail('Expected exception not thrown')
    } catch (RuntimeException e) {
      assert e.message == 'Fail fast'
    }
  }


  private static class StubChecker implements CircuitBreaker.Check {
    def passes = true
    @Override
    public Boolean check() {passes}
  }
}
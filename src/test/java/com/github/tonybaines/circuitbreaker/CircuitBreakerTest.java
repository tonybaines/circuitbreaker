package com.github.tonybaines.circuitbreaker;

import com.github.tonybaines.circuitbreaker.CircuitBreaker.Check;
import com.github.tonybaines.circuitbreaker.CircuitBreaker.RequestHandler;
import mockit.Mocked;
import mockit.Verifications;
import mockit.integration.junit4.JMockit;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

@RunWith(JMockit.class)
public class CircuitBreakerTest {
  Check CHECK_ALWAYS_OK = () -> true;
  Check CHECK_ALWAYS_FAILS = () -> false;
  Check CHECK_THROWS_EXCEPTION = () -> {throw new RuntimeException("Check threw an exception!");};
  RequestHandler<String, Integer> CLOSED_BEHAVIOUR = (x) -> {
    CircuitBreaker.log("Running 'Closed' behaviour");
    return x.length();
  };
  RequestHandler<String, Integer> OPEN_BEHAVIOUR = (x) -> {
    CircuitBreaker.log("Running 'Open' behaviour");
    throw new RuntimeException("Fail fast");
  };


  @Test
  public void whenInAClosedStateTheCircuitBreakerAllowsRequests() throws Exception {
    CircuitBreaker<String, Integer> circuitBreaker = CircuitBreaker.<String, Integer>newCircuitBreaker()
      .check(CHECK_ALWAYS_OK)
      .whenClosed(CLOSED_BEHAVIOUR)
      .whenOpen(OPEN_BEHAVIOUR)
      .build();

    expectClosedCircuitBehaviour(circuitBreaker);
  }

  @Test
  public void whenInAnOpenStateTheCircuitBreakerRejectsRequests() throws Exception {
    CircuitBreaker<String, Integer> circuitBreaker = CircuitBreaker.<String, Integer>newCircuitBreaker()
      .check(CHECK_ALWAYS_FAILS)
      .whenClosed(CLOSED_BEHAVIOUR)
      .whenOpen(OPEN_BEHAVIOUR)
      .build();

    expectOpenCircuitBehaviour(circuitBreaker);
  }

  @Test
  public void theCheckIsScheduled(@Mocked ScheduledExecutorService mockScheduler) throws Exception {
    CircuitBreaker.<String, Integer>newCircuitBreaker()
      .check(CHECK_ALWAYS_OK, Duration.ofSeconds(10))
      .withScheduler(mockScheduler)
      .build();

    new Verifications() {{
      mockScheduler.scheduleAtFixedRate((Runnable) any, anyLong, 10, TimeUnit.SECONDS);
    }};
  }

  @Test
  public void switchingFromClosedToOpenBehaviour() throws Exception {
    StubChecker stubChecker = new StubChecker();
    StubScheduledExecutor stubScheduler = new StubScheduledExecutor();

    CircuitBreaker<String, Integer> circuitBreaker = CircuitBreaker.<String, Integer>newCircuitBreaker()
      .check(stubChecker, Duration.ofSeconds(1))
      .whenClosed(CLOSED_BEHAVIOUR)
      .whenOpen(OPEN_BEHAVIOUR, Duration.ofSeconds(4))
      .withScheduler(stubScheduler)
      .build();

    stubScheduler.tick(); // t=1

    // When first started, the check passes and the circuit breaker follows 'Closed' behaviour
    expectClosedCircuitBehaviour(circuitBreaker);

    // When the check is made to fail, the circuit breaker follows 'Open' behaviour
    stubChecker.passes = false;
    stubScheduler.tick(); // t=2

    // ... expect it to fail
    expectOpenCircuitBehaviour(circuitBreaker);

    // The result of the check is ignored until the open-timeout has elapsed
    stubChecker.passes = true;
    stubScheduler.tick(); // t=3

    // ... expect it to still fail
    expectOpenCircuitBehaviour(circuitBreaker);

    // Wait for the open-timeout to complete
    stubScheduler.tick(3); // t=4,5,6

    // ... and we're back to 'Closed' behaviour
    expectClosedCircuitBehaviour(circuitBreaker);
  }

  @Test
  public void whenTheCheckStillFailsAfterTheOpenTimeoutTheOpenCircuitBehaviourContinues() throws Exception {
    StubChecker stubChecker = new StubChecker();
    StubScheduledExecutor stubScheduler = new StubScheduledExecutor();

    CircuitBreaker<String, Integer> circuitBreaker = CircuitBreaker.<String, Integer>newCircuitBreaker()
      .check(stubChecker, Duration.ofSeconds(1))
      .whenClosed(CLOSED_BEHAVIOUR)
      .whenOpen(OPEN_BEHAVIOUR, Duration.ofSeconds(4))
      .withScheduler(stubScheduler)
      .build();

    stubScheduler.tick(); // t=1
    // Starts off working
    expectClosedCircuitBehaviour(circuitBreaker);

    // then fails
    stubChecker.passes = false;
    stubScheduler.tick(); // t=2
    expectOpenCircuitBehaviour(circuitBreaker);

    // Wait for the open-timeout to complete
    stubScheduler.tick(4); // t=3,4,5,6
    // ... still failing
    expectOpenCircuitBehaviour(circuitBreaker);

    // Then recover
    stubChecker.passes = true;
    stubScheduler.tick(3); // t=7,8,9
    expectClosedCircuitBehaviour(circuitBreaker);
  }

  @Test
  public void whenTheCheckThrowsAnExceptionWhileClosedContinueInClosedMode() throws Exception {
    StubScheduledExecutor stubScheduler = new StubScheduledExecutor();

    CircuitBreaker<String, Integer> circuitBreaker = CircuitBreaker.<String, Integer>newCircuitBreaker()
      .check(CHECK_THROWS_EXCEPTION, Duration.ofSeconds(1))
      .whenClosed(CLOSED_BEHAVIOUR)
      .whenOpen(OPEN_BEHAVIOUR, Duration.ofSeconds(4))
      .withScheduler(stubScheduler)
      .build();

    stubScheduler.tick(); // t=1
    // Starts off working
    expectClosedCircuitBehaviour(circuitBreaker);

    stubScheduler.tick(); // t=2
    // ... and continues to work
    expectClosedCircuitBehaviour(circuitBreaker);

    stubScheduler.tick(); // t=3
    // ... and continues to work
    expectClosedCircuitBehaviour(circuitBreaker);
  }

  @Test
  public void whenTheCheckThrowsAnExceptionWhileOpenSwitchToClosedMode() throws Exception {
    StubScheduledExecutor stubScheduler = new StubScheduledExecutor();

    // First fail, then throw an exception
    Check unstableCheck = new Check() {
      AtomicInteger callCount = new AtomicInteger(0);
      public Boolean check() {
        if (callCount.incrementAndGet() <= 1) return false;
        else throw new RuntimeException("Check threw an exception!");
      }
    };

    CircuitBreaker<String, Integer> circuitBreaker = CircuitBreaker.<String, Integer>newCircuitBreaker()
      .check(unstableCheck, Duration.ofSeconds(1))
      .whenClosed(CLOSED_BEHAVIOUR)
      .whenOpen(OPEN_BEHAVIOUR, Duration.ofSeconds(4))
      .withScheduler(stubScheduler)
      .build();

    // Starts off failing
    expectOpenCircuitBehaviour(circuitBreaker);

    stubScheduler.tick(); // t=1

    // Wait for the open-timeout to complete
    stubScheduler.tick(4);

    // Then works
    expectClosedCircuitBehaviour(circuitBreaker);

    stubScheduler.tick(); // t=3
    // ... and continues to work despite the exceptions
    expectClosedCircuitBehaviour(circuitBreaker);

  }



  private void expectClosedCircuitBehaviour(CircuitBreaker<String, Integer> circuitBreaker) {
    assertThat(circuitBreaker.handle("foo"), is(3));
  }

  private void expectOpenCircuitBehaviour(CircuitBreaker<String, Integer> circuitBreaker) {
    try {
      circuitBreaker.handle("foo");
      fail("Expected exception not thrown");
    } catch (RuntimeException ignored) {
      assertThat(ignored.getMessage(), is("Fail fast"));
    }
  }

  private static class StubChecker implements Check {
    public boolean passes = true;

    @Override
    public Boolean check() {
      return passes;
    }
  }

}

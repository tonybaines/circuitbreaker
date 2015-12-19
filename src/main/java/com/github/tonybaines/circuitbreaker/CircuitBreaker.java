package com.github.tonybaines.circuitbreaker;

import com.github.tonybaines.circuitbreaker.statemachine.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of the Circuit Breaker pattern
 */

/* @startuml
 * [*] --> Closed
 * Closed --> Closed: [Check passes]
 * Closed --> Open: [Check failed]
 * Open --> Open: [While open timeout\nnot elapsed]
 * Open --> HalfOpen: [Open timeout elapsed]
 * HalfOpen --> Closed: [Check passes]
 * HalfOpen --> Open: [Check fails]
 *
 * note left of Closed
 *  Normal behaviour
 * end note
 *
 * note left of Open
 *  <b>Error behaviour</b>
 *  Continues until the
 *  openTimeout elapses
 * end note
 *
 * note right of HalfOpen
 *  Checking for recovery
 * end note
 * @enduml
 */
public class CircuitBreaker<INPUT, OUTPUT> implements CircuitBreakerMBean {
  private static final Logger LOG = LoggerFactory.getLogger(CircuitBreaker.class);
  private final Check check;
  private final StateConfiguration<INPUT, OUTPUT> stateConfiguration;

  private State<INPUT, OUTPUT> state;
  private String lastCheckDescription = "NONE";

  private CircuitBreaker(RequestHandler<INPUT, OUTPUT> openBehaviour, RequestHandler<INPUT, OUTPUT> closedBehaviour, Check check, Duration checkInterval, Duration openTimeout, ScheduledExecutorService scheduler) {
    this.stateConfiguration = new StateConfiguration<>( openTimeout, scheduler, openBehaviour, closedBehaviour);
    this.check = () -> {
      Check.Status status = check.check();
      this.lastCheckDescription = status.toString();
      LOG.debug("Check result: {}", lastCheckDescription);
      return status;
    };
    state = new ClosedState<>(stateConfiguration);
    setCurrentBehaviour();
    scheduleChecks(checkInterval, scheduler);
  }


  public static <INPUT, OUTPUT> CircuitBreakerBuilder<INPUT, OUTPUT> newCircuitBreaker() {
    return new CircuitBreakerBuilder<>();
  }

  public OUTPUT handle(INPUT input) {
    LOG.debug("In state {}", state.name());
    return state.responseTo(input);
  }

  @Override(/*MBean Interface*/)
  public String getCurrentState() {
    return state.name();
  }

  @Override(/*MBean Interface*/)
  public String getLastCheckDescription() {
    return lastCheckDescription;
  }

  @Override(/*MBean Interface*/)
  public void forceClosed() {
    state = new ForcedClosedState<>(stateConfiguration);
  }

  @Override(/*MBean Interface*/)
  public void forceOpen() {
    state = new ForcedOpenState<>(stateConfiguration);
  }

  @Override(/*MBean Interface*/)
  public void resetToNormalOperation() {
    state = new ClosedState<>(stateConfiguration);
  }

  private void scheduleChecks(Duration checkInterval, ScheduledExecutorService scheduler) {
    scheduler.scheduleAtFixedRate(this::setCurrentBehaviour,
      0, checkInterval.get(ChronoUnit.SECONDS), TimeUnit.SECONDS);
  }

  private void setCurrentBehaviour() {
    try {
      this.state = state.nextState(check.check());
    } catch (RuntimeException e) {
      LOG.warn("Caught an exception while running the check.  Defaulting to 'Closed' behaviour", e);
      this.state = new ClosedState<>(stateConfiguration);
    }
  }


  /*************************************************************************************/

  /**
   * Fluent builder of Circuit Breaker instances
   *
   * @param <INPUT>
   * @param <OUTPUT>
   */
  public static class CircuitBreakerBuilder<INPUT, OUTPUT> {
    /* Initialise with safe defaults */
    private Check check = () -> {
      throw new IllegalStateException("check behaviour not initialised");
    };
    private Duration checkInterval = Duration.ofSeconds(30);
    private RequestHandler<INPUT, OUTPUT> openBehaviour = x -> {
      throw new IllegalStateException("openBehaviour not initialised");
    };
    private RequestHandler<INPUT, OUTPUT> closedBehaviour = x -> {
      throw new IllegalStateException("closedBehaviour not initialised");
    };
    private Duration openTimeout = Duration.ofSeconds(10);

    // Don't initialise this, as a real ScheduledExecutorService is more expensive
    // check for a null reference before building instead
    private ScheduledExecutorService scheduler;

    public CircuitBreakerBuilder<INPUT, OUTPUT> check(Check check) {
      assertNotNull(check);
      assertNotNull(checkInterval);
      this.check = check;
      return this;
    }

    public CircuitBreakerBuilder<INPUT, OUTPUT> check(Check check, Duration checkInterval) {
      assertNotNull(checkInterval);
      check(check);
      this.checkInterval = checkInterval;
      return this;
    }

    public CircuitBreakerBuilder<INPUT, OUTPUT> whenOpen(RequestHandler<INPUT, OUTPUT> openBehaviour) {
      assertNotNull(openBehaviour);
      this.openBehaviour = openBehaviour;
      return this;
    }

    public CircuitBreakerBuilder<INPUT, OUTPUT> whenOpen(RequestHandler<INPUT, OUTPUT> openBehaviour, Duration openTimeout) {
      assertNotNull(openTimeout);
      whenOpen(openBehaviour);
      this.openTimeout = openTimeout;
      return this;
    }

    public CircuitBreakerBuilder<INPUT, OUTPUT> whenClosed(RequestHandler<INPUT, OUTPUT> closedBehaviour) {
      assertNotNull(closedBehaviour);
      this.closedBehaviour = closedBehaviour;
      return this;
    }

    public CircuitBreakerBuilder<INPUT, OUTPUT> withScheduler(ScheduledExecutorService scheduler) {
      this.scheduler = scheduler;
      return this;
    }

    public CircuitBreaker<INPUT, OUTPUT> build() {
      if (scheduler == null) {
        scheduler = new ScheduledThreadPoolExecutor(1);
      }
      return new CircuitBreaker<>(openBehaviour, closedBehaviour, check, checkInterval, openTimeout, scheduler);
    }

    private static void assertNotNull(Object value) {
      if (value == null) {
        throw new IllegalArgumentException("Null values are not allowed!");
      }
    }
  }
}

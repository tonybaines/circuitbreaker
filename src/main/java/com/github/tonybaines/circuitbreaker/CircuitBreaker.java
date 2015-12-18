package com.github.tonybaines.circuitbreaker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Implementation of the Circuit Breaker pattern
 *
 * @param <INPUT>
 * @param <OUTPUT>
 * @startuml [*] --> Closed
 * Closed --> Open: [Check failed]
 * Open --> Open: [While open timeout\nnot elapsed]
 * Open --> HalfOpen: [Open timeout elapsed]
 * HalfOpen --> Closed: [Check passes]
 * HalfOpen --> Open: [Check fails]
 * <p/>
 * note right of Closed
 * Normal behaviour
 * end note
 * <p/>
 * note left of Open
 * <b>Error behaviour</b>
 * Continues until the
 * openTimeout elapses
 * end note
 * <p/>
 * note right of HalfOpen
 * Checking for recovery
 * end note
 * @enduml
 */
public class CircuitBreaker<INPUT, OUTPUT> implements CircuitBreakerMBean {
  private static final Logger LOG = LoggerFactory.getLogger(CircuitBreaker.class);


  private enum StateLabel {OPEN, CLOSED;}
  private final ScheduledExecutorService scheduler;

  private Supplier<RequestHandler<INPUT,OUTPUT>> nextState;

  private final Check check;
  private RequestHandler<INPUT,OUTPUT> currentBehaviour;
  private Check currentCheck;
  private final Check suppliedCheck;
  private final RequestHandler<INPUT, OUTPUT> openStateBehaviour;
  private final RequestHandler<INPUT, OUTPUT> closedStateBehaviour;
  private String lastCheckDescription = "NONE";
  private StateLabel currentStateLabel = StateLabel.CLOSED;

  @Override(/*MBean Interface*/)
  public String getCurrentState() {
    return currentStateLabel.name();
  }

  @Override(/*MBean Interface*/)
  public String getLastCheckDescription() {
    return lastCheckDescription;
  }

  @Override(/*MBean Interface*/)
  public void forceClosed() {
    this.currentCheck = () -> new Check.Status(true, "FORCED-CLOSED");
  }

  @Override(/*MBean Interface*/)
  public void forceOpen() {
    this.currentCheck = () -> new Check.Status(false, "FORCED-OPEN");
  }

  @Override(/*MBean Interface*/)
  public void resetToNormalOperation() {
    this.currentCheck = this.suppliedCheck;
  }

  @FunctionalInterface
  public interface RequestHandler<REQ, RESP> {
    RESP responseTo(REQ REQ);
  }

  private CircuitBreaker(RequestHandler<INPUT,OUTPUT> openBehaviour, RequestHandler<INPUT,OUTPUT> closedBehaviour, Check check, Duration checkInterval, Duration openTimeout, ScheduledExecutorService scheduler) {

    // Take the supplied 'open' behaviour and add the extra state-machine logic
    this.openStateBehaviour = i -> {
      // Schedule a switch back to a 'closed' check once the timeout has elapsed
      scheduler.schedule(() -> nextState = closedState(),
        openTimeout.get(ChronoUnit.SECONDS), TimeUnit.SECONDS);

      nextState = openState();
      return openBehaviour.responseTo(i); // The result of the supplied 'open' behaviour
    };

    // Take the supplied 'closed' behaviour and add the extra state-machine logic
    this.closedStateBehaviour = i -> {
      nextState = closedState();
      return closedBehaviour.responseTo(i); // The result of the supplied 'closed' behaviour
    };

    this.suppliedCheck = check;
    this.currentCheck = suppliedCheck;
    this.check = () -> {
      Check.Status status = this.currentCheck.check();
      this.lastCheckDescription = status.toString();
      return status;
    };
    this.scheduler = scheduler;
    this.nextState = closedState();
    setCurrentBehaviour();
    scheduleChecks(checkInterval);
  }


  private Supplier<RequestHandler<INPUT,OUTPUT>> closedState() {
    LOG.info("Using 'closed' check behaviour");
    /*
     If the check succeeds, switch on the value
     If it throws an exception, default to the 'closed' behaviour
    */
    return () -> {
      try {
        if (check.check().status()) {
          currentStateLabel = StateLabel.CLOSED;
          return this.closedStateBehaviour;
        }
        else {
          currentStateLabel = StateLabel.OPEN;
          return this.openStateBehaviour;
        }
      } catch (RuntimeException e) {
        LOG.warn("Caught an exception while running the check.  Defaulting to 'Closed' behaviour", e);
        currentStateLabel = StateLabel.CLOSED;
        return closedStateBehaviour;
      }
    };

  }

  private Supplier<RequestHandler<INPUT,OUTPUT>> openState() {
    LOG.info("Using 'open' check behaviour");
    return () -> openStateBehaviour;
  }

  public static <INPUT, OUTPUT> CircuitBreakerBuilder<INPUT, OUTPUT> newCircuitBreaker() {
    return new CircuitBreakerBuilder<>();
  }

  public OUTPUT handle(INPUT input) {
    return currentBehaviour.responseTo(input);
  }

  private void scheduleChecks(Duration checkInterval) {
    scheduler.scheduleAtFixedRate(this::setCurrentBehaviour,
      0, checkInterval.get(ChronoUnit.SECONDS), TimeUnit.SECONDS);
  }

  private void setCurrentBehaviour() {
    this.currentBehaviour = nextState.get();
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
    private RequestHandler<INPUT,OUTPUT> openBehaviour = x -> {
      throw new IllegalStateException("openBehaviour not initialised");
    };
    private RequestHandler<INPUT,OUTPUT> closedBehaviour = x -> {
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

    public CircuitBreakerBuilder<INPUT, OUTPUT> whenOpen(RequestHandler<INPUT,OUTPUT> openBehaviour) {
      assertNotNull(openBehaviour);
      this.openBehaviour = openBehaviour;
      return this;
    }

    public CircuitBreakerBuilder<INPUT, OUTPUT> whenOpen(RequestHandler<INPUT,OUTPUT> openBehaviour, Duration openTimeout) {
      assertNotNull(openTimeout);
      whenOpen(openBehaviour);
      this.openTimeout = openTimeout;
      return this;
    }

    public CircuitBreakerBuilder<INPUT, OUTPUT> whenClosed(RequestHandler<INPUT,OUTPUT> closedBehaviour) {
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

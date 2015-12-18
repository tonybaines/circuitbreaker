package com.github.tonybaines.circuitbreaker

import com.google.common.collect.HashMultimap
import com.google.common.collect.Multimap
import groovy.util.logging.Slf4j

import java.util.concurrent.Callable
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * This simplified ScheduledExecutorService implementation makes some important assumptions in order
 * to keep the implementation manageable
 * <ul>
 *   <li>All times are integer numbers of seconds</li>
 *   <li>The return value of the schedule* methods aren't used</li>
 * </ul>
 */
@Slf4j
public class StubScheduledExecutor extends NullExecutorService implements ScheduledExecutorService {
  private final Clock clock = new Clock()
  private Multimap<Long, Runnable> runnableEvents = HashMultimap.create()
  private Multimap<Long, Callable> callableEvents = HashMultimap.create()

  @Override
  public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
    runnableEvents.put(clock.getTime() + delay, command)
    return null
  }

  @Override
  public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
    callableEvents.put(clock.getTime() + delay, callable)
    return null
  }

  @Override
  public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
    (1..10).each { i ->
      runnableEvents.put(clock.getTime() + (initialDelay + (period * i)), command)
    }
    return null
  }

  @Override
  public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
    return scheduleAtFixedRate(command, initialDelay, delay, unit)
  }

  public void tick() {
    long t = clock.tick()
    log.debug("tick(): t="+t)

    // Remove and run all the Runnables/Callables scheduled for
    // this 'tick'
    callableEvents.removeAll(t).each { e -> e.call() }
    runnableEvents.removeAll(t).each { e -> e.run() }
  }

  public void tick(int i) {
    i.times {  x -> tick() }
  }
}

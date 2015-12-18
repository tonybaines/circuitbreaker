package com.github.tonybaines.circuitbreaker;

public interface CircuitBreakerMBean {
  String getCurrentState();
  String getLastCheckDescription();
  void forceClosed();
  void forceOpen();
  void resetToNormalOperation();
}

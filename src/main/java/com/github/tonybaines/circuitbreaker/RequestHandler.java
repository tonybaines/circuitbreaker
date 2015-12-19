package com.github.tonybaines.circuitbreaker;

@FunctionalInterface
public interface RequestHandler<REQ, RESP> {
  RESP responseTo(REQ REQ);
}

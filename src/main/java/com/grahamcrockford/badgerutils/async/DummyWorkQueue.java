package com.grahamcrockford.badgerutils.async;

import java.util.Optional;

/**
 * Implementation of {@link WorkQueue} which doesn't allow any assistance to be
 * offered.  Effectively single-threads all data flows.
 */
public class DummyWorkQueue implements WorkQueue {

  @Override
  public void requestAssistance(Runnable assistanceRequest) {
  }

  @Override
  public void revokeRequest(Runnable assistanceRequest) {
  }

  @Override
  public Optional<Runnable> offerAssistance() {
    return Optional.empty();
  }
}
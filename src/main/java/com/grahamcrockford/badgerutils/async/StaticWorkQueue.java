package com.grahamcrockford.badgerutils.async;

import java.io.IOException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.google.common.base.Preconditions;

/**
 * Naive, blocking implementation of {@link WorkQueue}.
 */
public class StaticWorkQueue implements WorkQueue, Serializable {

  private static final long serialVersionUID = 1L;
  
  private static List<Runnable> requests = new ArrayList<>();
  private static int position;

  @Override
  public void requestAssistance(Runnable assistanceRequest) {
    synchronized (requests) {
      Preconditions.checkNotNull(assistanceRequest, "Null assistance request: " + assistanceRequest);
      requests.add(assistanceRequest);
    }

  }

  @Override
  public void revokeRequest(Runnable assistanceRequest) {
    synchronized (requests) {
      Preconditions.checkNotNull(assistanceRequest, "Null revoke request: " + assistanceRequest);
      requests.remove(assistanceRequest);
    }
  }

  @Override
  public Optional<Runnable> offerAssistance() {
    synchronized (requests) {
      if (requests.isEmpty()) {
        return Optional.empty();
      }
      if (position >= requests.size()) {
        position = 0;
      }
      return Optional.of(requests.get(position++));
    }
  }
  
  
  private void writeObject(java.io.ObjectOutputStream out) throws IOException {
    // No state
  }
  
  private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
    // No state
  }
    
  @SuppressWarnings("unused")
  private void readObjectNoData() throws ObjectStreamException {
    // No state
  }
}
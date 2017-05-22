/*
 * Copyright (C) 2017 Graham Crockford
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.grahamcrockford.badgerutils.async;

import java.io.IOException;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.google.common.base.Preconditions;

/**
 * Naive, blocking implementation of {@link WorkQueue} which delegates work
 * within a single JVM. 
 * 
 * <p>Definitely needs optimising if this is how the API ends up.</p>
 * 
 * <p>Allocates work requests on a round-robin basis, so given a single
 * caller of {@link #offerAssistance()} (assisters) and three callers of
 * {@link #requestAssistance(Runnable)} (requesters), as each requester
 * temporarily exhausts its supply of work, the assister moves onto
 * the next.  THis should result in a degree of self-balancing, but it's
 * pretty crude right now and needs a lot of work.</p>
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
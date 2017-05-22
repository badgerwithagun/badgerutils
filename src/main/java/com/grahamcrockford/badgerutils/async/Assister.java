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

import java.io.Serializable;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Generic worker which will attempt to help any running tasks process
 * their work quicker.
 */
public class Assister implements Runnable, Serializable {
  
  private static final long serialVersionUID = 1L;
  private static final Logger log = LoggerFactory.getLogger(Assister.class);
    
  private final WorkQueue workQueue;

  /**
   * Constructor.
   *
   * @param workQueue The {@link WorkQueue} to poll for available work.
   */
  public Assister(WorkQueue workQueue) {
    this.workQueue = workQueue;
  }

  /**
   * Repeatedly processes available work until there's none left,
   * then exits.  Also exits cleanly in response to an interrupt.
   * 
   * @see java.lang.Runnable#run()
   */
  @Override
  public void run() {
    log.info("{} starting", this);
    do {
      Optional<Runnable> request = workQueue.offerAssistance();
      if (!request.isPresent()) {
        break;
      }
      log.debug("{} found request {}", this, request.get());
      request.get().run();
      log.debug("{} completed request {}", this, request.get());
    } while (!Thread.interrupted());
    log.info("{} completed", this);
  }
}
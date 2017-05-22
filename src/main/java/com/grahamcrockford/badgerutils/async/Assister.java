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

  public Assister(WorkQueue workQueue) {
    this.workQueue = workQueue;
  }

  @Override
  public void run() {
    log.info(this + " starting");
    do {
      Optional<Runnable> request = workQueue.offerAssistance();
      if (!request.isPresent()) {
        break;
      }
      log.debug("{} found request {}", this, request.get());
      request.get().run();
      log.debug("{} completed request {}", this, request.get());
    } while (!Thread.interrupted());
    log.info(this + " completed");
  }
}
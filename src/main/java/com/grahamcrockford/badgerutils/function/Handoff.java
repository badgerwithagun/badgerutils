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

package com.grahamcrockford.badgerutils.function;

import java.sql.Connection;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.grahamcrockford.badgerutils.async.WorkQueue;
import com.grahamcrockford.badgerutils.base.AutoCloseables;
import com.grahamcrockford.badgerutils.base.CheckedExceptions;

/**
 * Terminator for a {@link Stream} which buffers the items, then once the buffer
 * is full, starts processing them. Other threads may assist with the
 * termination phase by registering with the {@link WorkQueue}.
 * 
 * <p>Effectively acts as a variable load balancer for a data pipeline.</p>
 * 
 * @author Graham Crockford (c) 2017
 *
 * @param <T> The type being processed.
 */
public final class Handoff<T> implements Consumer<T>, AutoCloseable {

  private static final Logger log = LoggerFactory.getLogger(Handoff.class);

  private final BlockingQueue<T> buffer;
  private final AtomicBoolean noMoreData = new AtomicBoolean(false);
  private final Supplier<Consumer<T>> delegate;
  private final WorkQueue workQueue;
  private final Consumer<T> synchronousConsumer;
  private Runnable assistanceRequest;

  /**
   * Starts building a new {@link Handoff}.
   * 
   * @param delegate A supplier or lambda which will generate a consumer on
   *          request. This will be called once for the handoff's own thread and
   *          once for each distinct assistance request.  This is intended to
   *          be friendly with ETL contexts where consumers are most likely
   *          to own a {@link Connection}, JMS destination or some other
   *          transactional context which can usually not be safely
   *          shared between threads.</p>
   * @return A builder.
   */
  public static <T> Builder<T> to(Supplier<Consumer<T>> delegate) {
    return new Builder<T>(delegate);
  }
  
  /**
   * Builder for {@link Handoff}.
   * 
   * @author grahamc (Graham Crockford)
   *
   * @param <T> The type being consumed.
   */
  public static final class Builder<T> {
    
    private int bufferSize = 1000;
    private Supplier<Consumer<T>> delegate;
    
    private Builder(Supplier<Consumer<T>> delegate) {
      this.delegate = delegate;
    }

    /**
     * The number of items to buffer. A larger buffer allows more efficient
     * sharing of large data volumes between large numbers of assistance threads
     * at high data volumes, but uses more memory and will make smaller data
     * volumes slower (since the buffer must be fully filled and then flushed
     * rather than the data being simply passed along).
     * 
     * <p>Defaults to 1000.</p>
     * 
     * @param bufferSize The buffer size.
     * @return The builder.
     */
    public Builder<T> withBufferSize(int bufferSize) {
      this.bufferSize = bufferSize;
      return this;
    }
    
    /**
     * The {@link WorkQueue} to be used to submit assistance requests.  These
     * will create a new delegate consumer and repeatedly poll the buffer
     * until it is empty, at which point they are permitted to move onto
     * other, parallel work requests.
     * 
     * @param workQueue The work queue.
     * @return The completed {@link Handoff}.
     */
    public Handoff<T> on(WorkQueue workQueue) {
      return new Handoff<>(workQueue, bufferSize, delegate);
    }
  }

  private Handoff(WorkQueue workQueue, int bufferSize, Supplier<Consumer<T>> delegate) {
    this.workQueue = workQueue;
    this.delegate = delegate;
    this.buffer = new ArrayBlockingQueue<>(bufferSize);
    this.synchronousConsumer = delegate.get();
  }

  /**
   * Receives a work item. May process the work in-line if the buffer is full,
   * otherwise batches it for assisters to help with.
   * 
   * <p>A work request is registered with {@link WorkQueue} when the buffer
   * first fills completely. This allows for smaller data volumes to avoid the
   * overhead of additional threads until it becomes clear that they are
   * actually needed.</p>
   * 
   * @see java.util.function.Consumer#accept(java.lang.Object)
   */
  @Override
  public void accept(T item) {
    Preconditions.checkState(!noMoreData.get(), "Accept called after close");

    // If there's no room in the buffer...
    while (!CheckedExceptions.callUnchecked(() -> buffer.offer(item))) {
      
      if (assistanceRequest == null) {
        // Register a help request
        this.assistanceRequest = this::assist;
        workQueue.requestAssistance(assistanceRequest);
      } else {
        log.debug("Producer outpacing consumers...");
      }
      
      // Remove an item, process it, then try again
      T first = CheckedExceptions.callUnchecked(() -> buffer.poll(1, TimeUnit.SECONDS));
      if (first != null) {
        synchronousConsumer.accept(first);
      }
    }
    
    if (assistanceRequest == null) {
      // Register a help request
      this.assistanceRequest = this::assist;
      workQueue.requestAssistance(assistanceRequest);
    }
  }

  /**
   * To assist, a caller will create a new instance of the delegate consumer,
   * and repeatedly poll our buffer until the buffer is depleted. If that
   * happens the caller will move onto some other work request. If there are no
   * other work requests, it will come straight back and try again. By this
   * mechanism, the number of assisters should self-balance around the available
   * work producers to achieve maximum throughput.
   * 
   * <p>TODO: There is a big weakness here, in that an exception in an
   * this thread could leave an item removed from the buffer but unprocessed.
   * Need to make the processing transactional.</p>
   */
  private void assist() {
    Consumer<T> consumer = delegate.get();
    try {
      do {
        T item = CheckedExceptions.callUnchecked(() -> buffer.poll(1, TimeUnit.SECONDS));
        if (item == null) {
          if (!noMoreData.get()) log.debug("Consumers outpacing producer...");
          return;
        }
        consumer.accept(item);
      } while (true);
    } finally {
      AutoCloseables.safeClose(consumer);
    }
  }

  /**
   * On close, we first attempt to work through all the remaining work items in
   * the buffer, then close the assistance request and the delegate consumer.
   * 
   * @see java.lang.AutoCloseable#close()
   */
  @Override
  public void close() {
    log.debug("Handoff closing");
    Preconditions.checkState(!noMoreData.get(), "Closed called twice");
    noMoreData.set(true);
    try {
      processRemaining();
    } finally {
      if (assistanceRequest != null) {
        workQueue.revokeRequest(assistanceRequest);
      }
      AutoCloseables.safeClose(synchronousConsumer);
    }
  }

  private void processRemaining() {
    do {
      T item = CheckedExceptions.callUnchecked(() -> buffer.poll(1, TimeUnit.SECONDS));
      if (item == null) {
        break;
      }
      synchronousConsumer.accept(item);
    } while (true);
  }
}
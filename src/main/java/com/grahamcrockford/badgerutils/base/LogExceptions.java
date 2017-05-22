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

package com.grahamcrockford.badgerutils.base;

import java.io.Serializable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.grahamcrockford.badgerutils.base.CheckedExceptions.ThrowingRunnable;

/**
 * Convenience methods which cause any exceptions thrown in a {@link Runnable}
 * to be logged appropriately. Useful when writing threads, which will otherwise
 * terminate without any notification if an exception hits the top of the stack.
 * 
 * <p>Currently tied to Log4J, but this stuff is so simply it's easy to
 * re-implement for your logger of choice.</p>
 * 
 * @author grahamc (Graham Crockford)
 */
public final class LogExceptions {

  private static final Logger log = LoggerFactory.getLogger(LogExceptions.class);

  /**
   * Runs the specified code. If any exceptions are caught, they are logged as
   * ERROR and then rethrown <em>unless</em> {@link Thread#interrupted()}. If
   * the thread has been interrupted, it is assumed that an
   * {@link InterruptedException} has been wrapped in a {@link RuntimeException}
   * at some point in the stack and the interrupted flag reset, to avoid checked
   * exceptions (see {@link CheckedExceptions#runUnchecked(ThrowingRunnable)}).
   * This means that the thread has been deliberately interrupted, so instead we
   * log this as an INFO condition and minimise the log message to just the
   * point in the code where the interruption originated.
   * 
   * <p>If the interrupted flag has been set but no {@link InterruptedException}
   * is found in the cause chain of the exception, we log an ERROR as normal,
   * but change the message to indicate this (probably incorrect) condition.</p>
   * 
   * <p>The interrupted flag is reset after it has been read, just in case
   * anything further up the stack needs it.</p>
   * 
   * @param runnable The code to run.
   */
  public static final void in(Runnable runnable) {
    try {
      runnable.run();
    } catch (RuntimeException e) {
      if (Thread.interrupted()) {
        
        Throwable t = e;
        while (t != null && !InterruptedException.class.isInstance(t)) {
          t = e.getCause();
        }
        
        if (t == null || t.getStackTrace().length == 0) {
          log.error("Thread interrupted but later exception thrown", e);
        } else {
          logInterrupt((InterruptedException) t);
        }
        
        Thread.currentThread().interrupt();
        
      } else {
        log.error("Caught exception", e);
      }
      throw e;
    }
  }
  
  /**
   * Logs that an interrupt occurred from a checked {@link InterruptedException} (as an INFO
   * with minimal stack information) and rethrows.  Intended for use where an {@link InterruptedException}
   * is expected and normal, but you nevertheless want some information on where the cause occurred
   * if the exception has bubbled a long way up the stack.
   * 
   * @param runnable The code to run.
   * @throws InterruptedException
   */
  public static final void onInterrupt(InterruptibleRunnable runnable) throws InterruptedException {
    try {
      runnable.run();
    } catch (InterruptedException e) {
      logInterrupt(e);
      throw e;
    }
  }

  private static void logInterrupt(InterruptedException e) {
    log.info("Thread interrupted at {}.{}:{}", e.getStackTrace()[0].getClassName(), e.getStackTrace()[0].getMethodName(), e.getStackTrace()[0].getLineNumber());
  }

  /**
   * Returns a {@link Runnable} which wraps the specified lambda in a call to
   * {@link #in(Runnable)}.  Usage:
   * 
   * <pre><code>executor.execute(LogExceptions.wrap(() -> {
   *  // Do your thing.
   *}));</code></pre>
   * 
   * @param delegate
   * @return
   */
  public static final Runnable wrap(Runnable delegate) {
    return (Runnable & Serializable) () -> in(delegate);
  }
  
  /**
   * Returns a {@link InterruptibleRunnable} which wraps the specified lambda in a call to
   * {@link #onInterrupt(InterruptibleRunnable)}.  Usage:
   * 
   * <pre><code>executor.execute(LogExceptions.log(() -> {
   *  throw new InterruptedException("Interrupt!");
   *}));</code></pre>
   * 
   * @param delegate
   * @return
   */
  public static final InterruptibleRunnable log(InterruptibleRunnable delegate) {
    return (InterruptibleRunnable & Serializable) () -> onInterrupt(delegate);
  }
  
  /**
   * Functional interface representing a {@link Runnable} which can be interrupted.
   * 
   * @author grahamc (Graham Crockford)
   */
  @FunctionalInterface
  public interface InterruptibleRunnable {
    public void run() throws InterruptedException;
  }
}
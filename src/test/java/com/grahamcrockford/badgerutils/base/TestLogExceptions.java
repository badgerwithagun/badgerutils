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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

import org.junit.After;
import org.junit.Test;
import org.mockito.Mockito;

import com.grahamcrockford.badgerutils.base.LogExceptions.InterruptibleRunnable;

import uk.org.lidalia.slf4jtest.TestLogger;
import uk.org.lidalia.slf4jtest.TestLoggerFactory;

public class TestLogExceptions {

  private TestLogger logger = TestLoggerFactory.getTestLogger(LogExceptions.class);

  /**
   * Base case. If no exceptions are thrown, no exceptions bubble up. Confirm
   * that the runnable is actually run!
   */
  @Test
  public void testNoExceptions() {
    Runnable mock = mock(Runnable.class);
    LogExceptions.in(mock);
    assertTrue(logger.getLoggingEvents().isEmpty());
    Mockito.verify(mock).run();
  }

  /**
   * Makes sure that unchecked exceptions are fully logged, with stack, and rethrown.
   */
  @Test
  public void testThrow() {
    try {
      LogExceptions.in(() -> {
        throw new IllegalStateException("Boo");
      });
    } catch (IllegalStateException e) {
      assertEquals(1, logger.getLoggingEvents().size());
      assertEquals("Caught exception", logger.getLoggingEvents().get(0).getMessage());
      assertEquals(IllegalStateException.class, logger.getLoggingEvents().get(0).getThrowable().get().getClass());
      return;
    }
    fail("No exception");
  }

  /**
   * Makes sure that if a thread is interrupted but the interrupt is not included
   * in an unchecked exception thrown after this, we still detect that the thread
   * was interrupted, but log an ERROR with the stack trace.
   */
  @Test
  public void testBadlyWrappedInterrupt() {
    try {
      LogExceptions.in(() -> {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Boo");
      });
    } catch (IllegalStateException e) {
      assertEquals(1, logger.getLoggingEvents().size());
      assertEquals("Thread interrupted but later exception thrown", logger.getLoggingEvents().get(0).getMessage());
      assertEquals(IllegalStateException.class, logger.getLoggingEvents().get(0).getThrowable().get().getClass());
      assertTrue(Thread.interrupted());
      return;
    }
    fail("No exception");
  }

  @Test
  public void testCorrectlyWrappedInterrupt() {
    try {
      Runnable unchecked = CheckedExceptions.uncheck(this::interrupts);
      Runnable logging = LogExceptions.wrap(unchecked);
      logging.run();
    } catch (RuntimeException e) {
      assertEquals(InterruptedException.class, e.getCause().getClass());
      checkLoggedInterrupt();
      assertTrue(Thread.interrupted());
      return;
    }
    fail("No exception");
  }

  @Test
  public void testUnWrappedInterrupt() throws InterruptedException {
    Thread.interrupted();
    try {
      InterruptibleRunnable logging = LogExceptions.log(this::interrupts);
      logging.run();
    } catch (InterruptedException e) {
      checkLoggedInterrupt();
      assertTrue(Thread.interrupted());
      return;
    }
    fail("No exception");
  }

  private void checkLoggedInterrupt() {
    assertEquals(1, logger.getLoggingEvents().size());
    assertFalse(logger.getLoggingEvents().get(0).getThrowable().isPresent());
    assertEquals("Thread interrupted at {}.{}:{}", logger.getLoggingEvents().get(0).getMessage());
    assertEquals(this.getClass().getName(), logger.getLoggingEvents().get(0).getArguments().get(0));
    assertEquals("interrupts", logger.getLoggingEvents().get(0).getArguments().get(1));
  }

  private void interrupts() throws InterruptedException {
    Thread.currentThread().interrupt();
    throw new InterruptedException("Interrupt");
  }

  @After
  public void clearLoggers() {
    TestLoggerFactory.clear();
  }
}

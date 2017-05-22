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

import java.util.concurrent.Callable;
import java.util.function.Supplier;

import com.google.common.base.Throwables;

/**
 * Checked exceptions are the subject of a never-ending debate in the Java
 * world. If you ever find your code cluttered for no apparently good reason,
 * these utilities may help.
 * 
 * <p>Note that <em>checked exceptions exist for a reason</em>, and these
 * utilities are not intended to allow you to forget they exist.  Use
 * judiciously to make your code more readable, but don't forget that
 * you're using Java.</p>
 * 
 * @author grahamc (Graham Crockford)
 */
public class CheckedExceptions {

  /**
   * Runs the specified {@link Runnable}, wrapping any checked exceptions thrown
   * in a {@link RuntimeException}. These are still thrown, but it is not then
   * necessary to use a {@code try...catch} block around the call or rethrow the
   * checked exception in your method signature.
   * 
   * <p>In use, the following:</p>
   * 
   * <pre><code>try {
   *   doSomething();
   * } catch (SomeCheckedException e) {
   *   throw new RuntimeException(e);
   * }</code></pre>
   * 
   * <p>Can be replaced with:</p>
   * 
   * <pre><code>CheckedExceptions.runUnchecked(this::doSomething);</code></pre>
   *   
   * <p>Specifically, exceptions are treated as follows:</p>
   * 
   * <ul>
   *  <li>Unchecked exceptions are simply rethrown.</li>
   *  <li>If {@link InterruptedException} is thrown, the interrupt flag is reset (so we
   * don't hide that an interrupt occurred) and the exception is rethrown,
   * wrapped in a {@link RuntimeException}.  <strong>Note</strong> that although this
   * should achieve an interrupt as intended in most circumstances, if you are writing
   * concurrent code, it is arguable that there may be much cleaner ways to shut down.
   * Please use with due consideration for why {@link InterruptedException} exists in
   * the first place!</li>
   * </li>
   *  <li>All other checked exceptions are simply wrapped in a
   *  {@link RuntimeException} and rethrown.</li>
   * </ul>
   * 
   * @param runnable The code to run, which may throw checked exceptions.
   * @throws A {@link RuntimeException} wrapping any checked exceptions thrown.
   */
  public static void runUnchecked(ThrowingRunnable runnable) {
    try {
      runnable.run();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(e);
    } catch (Throwable t) {
      Throwables.throwIfUnchecked(t);
      throw new RuntimeException(t);
    }
  }

  /**
   * Equivalent of {@link #runUnchecked(ThrowingRunnable)}, but runs
   * a {@link Callable}, returning the value returned.  See
   * {@link #runUnchecked(ThrowingRunnable)} for full information.
   * 
   * @param callable The code to run, which may throw checked exceptions.
   * @return The value returned by <code>callable</code>.
   * @throws A {@link RuntimeException} wrapping any checked exceptions thrown.
   */
  public static <T> T callUnchecked(Callable<T> callable) {
    try {
      return callable.call();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException(e);
    } catch (Throwable t) {
      Throwables.throwIfUnchecked(t);
      throw new RuntimeException(t);
    }
  }

  /**
   * Wraps a checked exception-throwing lambda in another {@link Runnable} which
   * uses {@link #runUnchecked(ThrowingRunnable)} to convert checked exceptions
   * to unchecked exceptions.  Can be useful when trying to write lambdas
   * but where the enclosed code throws checked exceptions and the consumer
   * only accepts a conventional {@link FunctionalInterface}.
   * 
   * <p>For example, the following deals with the unspecified {@link Exception} thrown
   * by an {@link AutoCloseable} resource used in a try with resources:</p>
   * 
   * <pre><code>executor.execute(CheckedExceptions.uncheck(() -> { 
   *  try (Connection c = getDBConnection();
   *    ...
   *  }
   *}));</code></pre>
   * 
   * @param runnable The lambda to wrap.
   * @return The now non-throwing {@link Runnable}.
   */
  public static Runnable uncheck(ThrowingRunnable runnable) {
    return () -> runUnchecked(runnable);
  }

  /**
   * Equivalent of {@link #uncheck(ThrowingRunnable)}, but wraps a {@link Callable}.
   * See {@link #uncheck(ThrowingRunnable)} for more information.
   * 
   * <p>The {@link Callable} is effectively converted into a {@link Supplier}.</p>
   * 
   * @param callable The lambda to wrap.
   * @returnT he now non-throwing {@link Supplier}.
   */
  public static <T> Supplier<T> uncheck(Callable<T> callable) {
    return () -> callUnchecked(callable);
  }

  /**
   * Functional interface representing a {@link Runnable} which throws a checked
   * {@link Throwable}
   * 
   * @author grahamc (Graham Crockford)
   */
  @FunctionalInterface
  public interface ThrowingRunnable {
    public void run() throws Exception;
  }
}

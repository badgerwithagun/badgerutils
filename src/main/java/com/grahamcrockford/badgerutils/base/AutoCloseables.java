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

import com.grahamcrockford.badgerutils.base.CheckedExceptions.ThrowingRunnable;

/**
 * Useful methods for dealing with instances of {@link AutoCloseable}.
 * 
 * @author grahamc (Graham Crockford)
 */
public final class AutoCloseables {
  
  /**
   * Closes an object <em>if</em> if is {@link AutoCloseable} and
   * <em>if</em> it is not null, converting any checked exceptions
   * into unchecked exceptions using {@link CheckedExceptions#runUnchecked(ThrowingRunnable)}.
   * 
   * @param o The object to close, if required.
   */
  public static <T> void safeClose(Object o) {
    if (o == null) return;
    if (AutoCloseable.class.isInstance(o)) {
      CheckedExceptions.runUnchecked(((AutoCloseable) o)::close);
    }
  }
}
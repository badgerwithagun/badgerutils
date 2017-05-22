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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import com.google.common.collect.ImmutableList;

/**
 * Tests for {@link Batcher}.
 * 
 * @author grahamc (Graham Crockford)
 */
public class TestBatcher {
  
  @Mock private Consumer<Iterable<Integer>> delegate;
  
  @Before
  public void before() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void testNoData() {
    try (Batcher<Integer> batch = Batcher.batch(delegate)) {
    }
    Mockito.verifyZeroInteractions(delegate);
  }
  
  @Test
  public void testAutoClose() throws Exception {
    AutoCloseableConsumer consumer = Mockito.mock(AutoCloseableConsumer.class);
    try (Batcher<Integer> batch = Batcher.batch(consumer)) {
    }
    verifyZeroInteractions(delegate);
    verify(consumer).close();
  }
  
  @Test
  public void testPreventUseOnDifferentThreadToCreator() throws InterruptedException, ExecutionException {
    try (Batcher<Integer> batch = Batcher.batch(delegate)) {
      Boolean failed = Executors.newSingleThreadExecutor().submit(() -> {
        try {
          batch.accept(2);
        } catch (ConcurrentModificationException e) {
          return true;
        }
        return false;
      }).get();
      Assert.assertTrue(failed);
    }
  }
  
  @Test
  public void testPreventCloseOnDifferentThreadToCreator() throws InterruptedException, ExecutionException {
    try (Batcher<Integer> batch = Batcher.batch(delegate)) {
      Boolean failed = Executors.newSingleThreadExecutor().submit(() -> {
        try {
          batch.accept(2);
        } catch (ConcurrentModificationException e) {
          return true;
        }
        return false;
      }).get();
      Assert.assertTrue(failed);
    }
  }
  
  @Test
  public void testNoFlushUntilClose() {
    
    // 1...10
    List<Integer> items = ImmutableList.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
    
    // Shove them into the batcher and close it.  Until closed, we shouldn't see anything flushed.
    try (Batcher<Integer> batch = Batcher.batch(10, delegate)) {
      items.forEach(batch);
      verifyZeroInteractions(delegate);
    }
    
    // Make sure the delegate was flushed to
    verify(delegate).accept(items);
  }
  
  @Test
  public void testInterimFlush() {
    
    // Shove them into the batcher and close it
    try (Batcher<Integer> batch = Batcher.batch(10, delegate)) {
      ImmutableList.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11).forEach(batch);
      
      // Should have flushed once
      verify(delegate).accept(ImmutableList.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
    }
    
    // Make sure the delegate was flushed to
    verify(delegate).accept(ImmutableList.of(11));
  }
  
  @Test
  public void testInterimFlushTwice() {
    
    // Shove them into the batcher and close it
    try (Batcher<Integer> batch = Batcher.batch(5, delegate)) {
      ImmutableList.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14).forEach(batch);
      
      // Should have flushed twice
      verify(delegate).accept(ImmutableList.of(1, 2, 3, 4, 5));
      verify(delegate).accept(ImmutableList.of(6, 7, 8, 9, 10));
    }
    
    // Make sure the delegate was flushed to
    verify(delegate).accept(ImmutableList.of(11, 12, 13, 14));
  }
  
  
  private interface AutoCloseableConsumer extends Consumer<Iterable<Integer>>, AutoCloseable {
    
  }
}

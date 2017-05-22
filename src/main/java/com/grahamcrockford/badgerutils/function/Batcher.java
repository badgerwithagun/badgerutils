package com.grahamcrockford.badgerutils.function;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.function.Consumer;

import com.grahamcrockford.badgerutils.base.AutoCloseables;

/**
 * Delegating {@link Consumer} which takes <code>T</code> as input and delegates
 * every <code>batchSize</code> records to a consumer of
 * <code>{@literal List<T>}</code>. On close, it flushes the remaining records
 * to the delegate as a final list.
 * 
 * <p>This class is not thread safe, and will aggressively throw
 * {@link ConcurrentModificationException} if used across threads.</p>
 *
 * @param <T> The type processed.
 */
public final class Batcher<T> implements Consumer<T>, AutoCloseable {

  private static final int DEFAULT_BATCH_SIZE = 1000;

  private final int batchSize;
  private List<T> batch;
  private final Consumer<Iterable<T>> delegate;
  private final WeakReference<Thread> thread;

  /**
   * Creates a {@link Batcher} which flushes to the supplied {@code delegate}
   * every 1000 items. For a custom batch size, use
   * {@link #batch(int, Consumer)}.
   * 
   * <p>If the {@code delegate} is {@link AutoCloseable}, it is automatically
   * closed when the batcher is closed.</p>
   * 
   * @param delegate The delegate consumer.
   * @return The batcher.
   */
  public static <T> Batcher<T> batch(Consumer<Iterable<T>> delegate) {
    return new Batcher<>(DEFAULT_BATCH_SIZE, delegate);
  }

  /**
   * Creates a {@link Batcher} which flushes to the supplied {@code delegate}
   * every {@code batchSize} items.
   * 
   * <p>If the {@code delegate} is {@link AutoCloseable}, it is automatically
   * closed when the batcher is closed.</p>
   * 
   * @param batchSize The maximum number of items per batch. May be smaller for
   *          the final flush on {@link #close()}.
   * @param delegate The delegate consumer.
   * @return The batcher.
   */
  public static <T> Batcher<T> batch(int batchSize, Consumer<Iterable<T>> delegate) {
    return new Batcher<>(batchSize, delegate);
  }

  /**
   * Constructor.
   * 
   * @param batchSize The number of items to accept before flushing the batch to
   *          the delegate.
   * @param delegate The delegate consumer.
   */
  private Batcher(int batchSize, Consumer<Iterable<T>> delegate) {
    this.batchSize = batchSize;
    this.delegate = delegate;
    this.batch = new ArrayList<>(batchSize);
    this.thread = new WeakReference<>(Thread.currentThread());
  }

  /**
   * Receives the item and batches it. If the batch has reached the configured
   * {@code batchSize}, the batch is flushed to the delegate consumer.
   * 
   * @see java.util.function.Consumer#accept(java.lang.Object)
   */
  @Override
  public void accept(T t) {
    checkConcurrent();
    if (batch.size() >= batchSize) {
      flush();
    }
    batch.add(t);
  }

  /**
   * Takes the currently buffered items and passes them as a list to the
   * delegate.
   */
  private void flush() {
    delegate.accept(Collections.unmodifiableList(batch));
    batch = new ArrayList<>(batchSize);
  }

  /**
   * Flushes anything remaining in the current batch.
   * 
   * @see java.lang.AutoCloseable#close()
   */
  @Override
  public void close() {
    checkConcurrent();
    if (!batch.isEmpty()) {
      flush();
    }
    AutoCloseables.safeClose(delegate);
  }

  private void checkConcurrent() {
    if (thread.get() == null || Thread.currentThread().getId() != thread.get().getId()) {
      throw new ConcurrentModificationException(
          "Batcher must not be shared between threads. Create instances on demand in threads as they are needed.");
    }
  }
}
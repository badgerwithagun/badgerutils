package com.grahamcrockford.badgerutils.async;

import java.util.Optional;

/**
 * Single point of registry for threads which wand to parallelise work but
 * which are operating within a fixed thread pool and therefore can't safely
 * submit new work themselves without risking thread starvation.
 * 
 * @author grahamc (Graham Crockford)
 */
public interface WorkQueue {
  
  /**
   * Registers a runnable as an assistance request.
   * 
   * <p>This may (will) get run <em>repeatedly</em>, even <em>after
   * {@link #revokeRequest(Runnable)} has been called</em>. The caller should
   * make sure that the {@link Runnable} can handle this condition.</p>
   * 
   * <p>It may also <em>not get called at all</em>. Do not assume it will. It
   * should either assist in a queue of work which is otherwise being operated
   * over by the calling thread, or potentially execute a single piece of work
   * which will otherwise be executed later by the calling thread if the
   * assistance request has not been processed by a target time.</p>
   * 
   * @param assistanceRequest The runnable.
   */
  public void requestAssistance(Runnable assistanceRequest);
  
  /**
   * Revokes a prior assistance request.  Assisters will <em>eventually</em>
   * stop calling it, but do not assume that this will happen immediately.
   * 
   * <p>This <strong>must</strong> be called when the work is completed,
   * or it will continue getting called indefinitely.  TODO this isn't
   * good enough!</p>
   * 
   * @param assistanceRequest The runnable.
   */
  public void revokeRequest(Runnable assistanceRequest);
  
  /**
   * Threads which want to provide assistance to a caller of
   * {@link #requestAssistance(Runnable)} can repeatedly call this to get
   * assistance requests to process. If nothing is returned, this indicates that
   * there are no more active assistance requests.
   * 
   * @return AN active assistance request, if any.
   */
  public Optional<Runnable> offerAssistance();
}
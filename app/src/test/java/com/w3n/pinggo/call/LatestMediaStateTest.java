package com.w3n.pinggo.call;

import org.junit.Test;
import static org.junit.Assert.*;

public class LatestMediaStateTest {
  @Test public void rapidDoubleTapIsAppliedInOrder() {
    LatestMediaState state = new LatestMediaState(false);
    assertTrue(state.toggle());
    assertTrue(state.begin());
    assertFalse(state.toggle());
    assertFalse(state.needsApply());
    state.complete(true);
    assertTrue(state.needsApply());
    assertFalse(state.begin());
    state.complete(true);
    assertFalse(state.applied());
    assertFalse(state.needsApply());
  }
  @Test public void rapidTripleTapCoalescesToLastIntent() {
    LatestMediaState state = new LatestMediaState(true);
    state.toggle(); state.begin(); state.toggle(); state.toggle();
    state.complete(true);
    assertFalse(state.desired());
    assertFalse(state.needsApply());
  }
  @Test public void failureRollsBackUi() {
    LatestMediaState state = new LatestMediaState(false);
    state.toggle(); state.begin(); state.complete(false);
    assertFalse(state.desired());
    assertFalse(state.needsApply());
  }
  @Test public void failureDoesNotOverwriteNewerIntent() {
    LatestMediaState state = new LatestMediaState(true);
    state.toggle(); state.begin(); state.toggle(); state.complete(false);
    assertTrue(state.desired());
    assertFalse(state.needsApply());
  }
}

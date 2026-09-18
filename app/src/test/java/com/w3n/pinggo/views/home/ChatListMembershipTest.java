package com.w3n.pinggo.views.home;

import org.junit.Test;
import static org.junit.Assert.*;

public class ChatListMembershipTest {
  @Test public void hidesUnrelatedConferencePairEvenIfStoredLocally() {
    assertFalse(ChatListMembership.visible("919867400865_917710867126", "919867180719"));
    assertTrue(ChatListMembership.visible("919867400865_917710867126", "+919867400865"));
  }
  @Test public void preservesOwnDirectChatsAndGroups() {
    assertTrue(ChatListMembership.visible("917710867126_919867180719", "919867180719"));
    assertTrue(ChatListMembership.visible("grp_example", "919867180719"));
    assertFalse(ChatListMembership.visible("1_2_3", "1"));
    assertFalse(ChatListMembership.visible("1_", "1"));
  }
}

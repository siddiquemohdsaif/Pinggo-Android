package com.w3n.pinggo.views.home;

final class ChatListMembership {
  static boolean visible(String chatId, String accountId) {
    if (chatId == null) return false;
    if (chatId.startsWith("grp_")) return true;
    String own = normalize(accountId);
    String[] members = chatId.split("_", -1);
    return !own.isEmpty() && members.length == 2
        && !normalize(members[0]).isEmpty() && !normalize(members[1]).isEmpty()
        && (own.equals(normalize(members[0])) || own.equals(normalize(members[1])));
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim().replace("<plus>", "").replaceFirst("^\\+", "");
  }
}

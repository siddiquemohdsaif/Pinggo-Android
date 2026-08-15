package com.w3n.pinggo.utils;

import com.w3n.pinggo.modals.Chat;

public final class ChatsData {
    private ChatsData() {
    }

    public static Chat[] getChats() {
        String[] contactNames = ContactsData.getContactNames();
        Chat[] chats = new Chat[contactNames.length];
        for (int i = 0; i < contactNames.length; i++) {
            chats[i] = new Chat(contactNames[i]);
        }
        return chats;
    }
}

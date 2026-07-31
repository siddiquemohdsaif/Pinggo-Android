package com.w3n.wavestream.utils;

import com.w3n.wavestream.modals.Chat;

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

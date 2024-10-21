package org.example.base;

import org.example.types.Message;
import java.util.HashMap;
import java.util.Map;

public class MessageStore {
    private final Map<String, Message> storage;

    public MessageStore() {
        this.storage = new HashMap<>();
    }

    public void storeMessage(String key, Message content) {
        storage.put(key, content);
    }

    public Message retrieveMessage(String key) {
        return storage.get(key);
    }

    public void deleteMessage(String key) {
        storage.remove(key);
    }

    public Map<String, Message> getStorage() {
        return storage;
    }
}

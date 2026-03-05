package com.roborally.common.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.roborally.common.enums.MessageType;
import java.util.Map;

/**
 * Base message format for all WebSocket communication.
 * Every message has a "type" field and optional payload data.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Message {

    @JsonProperty("type")
    private MessageType type;

    @JsonProperty("data")
    private Map<String, Object> data;

    public Message() {}

    public Message(MessageType type) {
        this.type = type;
    }

    public Message(MessageType type, Map<String, Object> data) {
        this.type = type;
        this.data = data;
    }

    public MessageType getType() { return type; }
    public void setType(MessageType type) { this.type = type; }
    public Map<String, Object> getData() { return data; }
    public void setData(Map<String, Object> data) { this.data = data; }

    // Convenience: get a single value from data
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        if (data == null) return null;
        return (T) data.get(key);
    }

    public String getString(String key) {
        Object val = get(key);
        return val != null ? val.toString() : null;
    }

    public Integer getInt(String key) {
        Object val = get(key);
        if (val instanceof Number) return ((Number) val).intValue();
        if (val instanceof String) return Integer.parseInt((String) val);
        return null;
    }

    public static Message of(MessageType type) {
        return new Message(type);
    }

    public static Message of(MessageType type, Map<String, Object> data) {
        return new Message(type, data);
    }

    public static Message error(String message) {
        return new Message(MessageType.ERROR, Map.of("message", message));
    }
}

package com.roborally.common.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roborally.common.enums.MessageType;
import org.junit.jupiter.api.*;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Message protocol class.
 */
class MessageTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("Message.of(type): creates message with type, no data")
    void of_typeOnly() {
        Message msg = Message.of(MessageType.LOGOUT_SUCCESS);

        assertEquals(MessageType.LOGOUT_SUCCESS, msg.getType());
        assertNull(msg.getData());
    }

    @Test
    @DisplayName("Message.of(type, data): creates message with data")
    void of_typeAndData() {
        Message msg = Message.of(MessageType.LOGIN_SUCCESS, Map.of(
                "userId", 42L, "username", "player1"));

        assertEquals(MessageType.LOGIN_SUCCESS, msg.getType());
        assertEquals(42L, msg.getData().get("userId"));
        assertEquals("player1", msg.getData().get("username"));
    }

    @Test
    @DisplayName("Message.error: creates ERROR message with message text")
    void error_createsErrorMessage() {
        Message msg = Message.error("Something went wrong");

        assertEquals(MessageType.ERROR, msg.getType());
        assertEquals("Something went wrong", msg.getData().get("message"));
    }

    @Test
    @DisplayName("getString: returns string value from data")
    void getString_returnsValue() {
        Message msg = new Message(MessageType.LOGIN, Map.of("username", "test"));

        assertEquals("test", msg.getString("username"));
        assertNull(msg.getString("nonexistent"));
    }

    @Test
    @DisplayName("getInt: returns integer from number value")
    void getInt_fromNumber() {
        Message msg = new Message(MessageType.LOGIN, Map.of("count", 5));

        assertEquals(5, msg.getInt("count"));
    }

    @Test
    @DisplayName("getInt: returns integer from string value")
    void getInt_fromString() {
        Message msg = new Message(MessageType.LOGIN, Map.of("count", "7"));

        assertEquals(7, msg.getInt("count"));
    }

    @Test
    @DisplayName("getInt: returns null for missing key")
    void getInt_missingKey_returnsNull() {
        Message msg = new Message(MessageType.LOGIN, Map.of());

        assertNull(msg.getInt("nope"));
    }

    @Test
    @DisplayName("get: returns null when data is null")
    void get_nullData_returnsNull() {
        Message msg = new Message(MessageType.LOGIN);

        assertNull(msg.get("anything"));
        assertNull(msg.getString("anything"));
    }

    @Test
    @DisplayName("JSON serialization: type field is serialized")
    void jsonSerialization_typeField() throws Exception {
        Message msg = Message.of(MessageType.LOGIN_SUCCESS, Map.of("userId", 1));
        String json = mapper.writeValueAsString(msg);

        assertTrue(json.contains("\"type\":\"LOGIN_SUCCESS\""));
        assertTrue(json.contains("\"userId\":1"));
    }

    @Test
    @DisplayName("JSON deserialization: parses from JSON string")
    void jsonDeserialization_parsesCorrectly() throws Exception {
        String json = "{\"type\":\"REGISTER\",\"data\":{\"username\":\"test\",\"email\":\"t@m.de\",\"password\":\"pw\"}}";

        Message msg = mapper.readValue(json, Message.class);

        assertEquals(MessageType.REGISTER, msg.getType());
        assertEquals("test", msg.getString("username"));
        assertEquals("t@m.de", msg.getString("email"));
    }

    @Test
    @DisplayName("JSON: null data is excluded from output")
    void jsonSerialization_nullDataExcluded() throws Exception {
        Message msg = Message.of(MessageType.LOGOUT_SUCCESS);
        String json = mapper.writeValueAsString(msg);

        assertFalse(json.contains("\"data\""));
    }
}

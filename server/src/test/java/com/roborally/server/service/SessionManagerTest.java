package com.roborally.server.service;

import com.roborally.common.enums.MessageType;
import com.roborally.common.protocol.Message;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SessionManagerTest {

    @Test
    void sendMessage_serializesConcurrentWritesPerSession() throws Exception {
        SessionManager manager = new SessionManager();
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("session-1");
        when(session.isOpen()).thenReturn(true);

        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxConcurrent = new AtomicInteger();
        doAnswer(invocation -> {
            int concurrent = inFlight.incrementAndGet();
            maxConcurrent.accumulateAndGet(concurrent, Math::max);
            try {
                Thread.sleep(50);
            } finally {
                inFlight.decrementAndGet();
            }
            return null;
        }).when(session).sendMessage(org.mockito.ArgumentMatchers.<WebSocketMessage<?>>any());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Void> task = () -> {
                ready.countDown();
                if (!start.await(2, TimeUnit.SECONDS)) {
                    throw new TimeoutException("start latch not released");
                }
                manager.sendMessage(session, Message.of(MessageType.LOBBY_LIST, Map.of("lobbies", java.util.List.of())));
                return null;
            };

            Future<Void> first = executor.submit(task);
            Future<Void> second = executor.submit(task);

            if (!ready.await(2, TimeUnit.SECONDS)) {
                throw new TimeoutException("tasks did not become ready");
            }
            start.countDown();

            first.get(2, TimeUnit.SECONDS);
            second.get(2, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(2, TimeUnit.SECONDS);
        }

        assertEquals(1, maxConcurrent.get(), "writes to the same WebSocketSession should be serialized");
    }
}

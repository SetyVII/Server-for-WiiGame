package model;

import io.javalin.websocket.WsContext;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClientStore {
    // Thread-safe map to store connections for Player 1 and Player 2.
    // Keys: 1 (Player 1), 2 (Player 2)
    public static final Map<Integer, WsContext> players = new ConcurrentHashMap<>();
    
    // Unity's WebSocket connection
    public static volatile WsContext unitySocket = null;

    // Track ConnectionState for each active session using sessionId as the key
    public static final Map<String, ConnectionState> sessionStates = new ConcurrentHashMap<>();
}

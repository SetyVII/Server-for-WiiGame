package com.tfg.motioncontroller.data.network

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebSocketClient @Inject constructor() {

    companion object {
        private const val TAG = "WebSocketClient"
    }

    private var webSocket: WebSocket? = null
    private val client: OkHttpClient
    private val json = Json { 
        encodeDefaults = true 
        ignoreUnknownKeys = true
    }

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _playerId = MutableStateFlow<Int?>(null)
    val playerId: StateFlow<Int?> = _playerId.asStateFlow()

    private val _lastEvent = MutableStateFlow<ServerMessage?>(null)
    val lastEvent: StateFlow<ServerMessage?> = _lastEvent.asStateFlow()

    private var lastErrorMessage: String = ""
    private var onMessageReceived: ((String) -> Unit)? = null

    init {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .pingInterval(15, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .build()
    }

    fun connect(url: String) {
        Log.d(TAG, "Intentando conectar a: $url")
        
        if (_connectionState.value == ConnectionState.CONNECTING || 
            _connectionState.value == ConnectionState.CONNECTED) {
            Log.w(TAG, "Ya hay una conexion en progreso o activa")
            return
        }

        _connectionState.value = ConnectionState.CONNECTING
        lastErrorMessage = ""
        _playerId.value = null
        _lastEvent.value = null

        try {
            val request = Request.Builder()
                .url(url)
                .build()

            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.i(TAG, "WebSocket conectado exitosamente")
                    _connectionState.value = ConnectionState.CONNECTED
                    // Enviar join automaticamente
                    send(json.encodeToString(JoinMessage()))
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    Log.d(TAG, "Mensaje recibido: $text")
                    handleIncomingMessage(text)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    Log.i(TAG, "WebSocket cerrando: code=$code, reason=$reason")
                    _connectionState.value = ConnectionState.DISCONNECTED
                    _playerId.value = null
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.i(TAG, "WebSocket cerrado: code=$code, reason=$reason")
                    _connectionState.value = ConnectionState.DISCONNECTED
                    _playerId.value = null
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "WebSocket error: ${t.message}", t)
                    lastErrorMessage = t.message ?: "Error desconocido de conexion"
                    _connectionState.value = ConnectionState.ERROR
                    _playerId.value = null
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error al crear WebSocket: ${e.message}", e)
            lastErrorMessage = e.message ?: "Error al crear conexion"
            _connectionState.value = ConnectionState.ERROR
        }
    }

    fun disconnect() {
        Log.i(TAG, "Desconectando WebSocket")
        webSocket?.close(1000, "Cliente desconectando")
        webSocket = null
        _connectionState.value = ConnectionState.DISCONNECTED
        _playerId.value = null
        _lastEvent.value = null
    }

    fun getLastError(): String = lastErrorMessage

    fun setMessageListener(listener: (String) -> Unit) {
        onMessageReceived = listener
    }

    private fun handleIncomingMessage(text: String) {
        try {
            when {
                text.contains("\"type\":\"assignRole\"") -> {
                    val role = json.decodeFromString(ServerMessage.AssignRole.serializer(), text)
                    _playerId.value = role.playerId
                    Log.i(TAG, "Rol asignado: Jugador ${role.playerId}")
                }
                text.contains("\"type\":\"error\"") -> {
                    val error = json.decodeFromString(ServerMessage.Error.serializer(), text)
                    lastErrorMessage = error.message
                    _lastEvent.value = error
                    Log.e(TAG, "Error del servidor: ${error.message}")
                }
                text.contains("\"type\":\"collision\"") -> {
                    _lastEvent.value = json.decodeFromString(ServerMessage.Collision.serializer(), text)
                }
                text.contains("\"type\":\"death\"") -> {
                    _lastEvent.value = json.decodeFromString(ServerMessage.Death.serializer(), text)
                }
                text.contains("\"type\":\"pickup\"") -> {
                    _lastEvent.value = json.decodeFromString(ServerMessage.Pickup.serializer(), text)
                }
                text.contains("\"type\":\"ui_update\"") -> {
                    _lastEvent.value = json.decodeFromString(ServerMessage.UIUpdate.serializer(), text)
                }
                text.contains("\"type\":\"screen_effect\"") -> {
                    _lastEvent.value = json.decodeFromString(ServerMessage.ScreenEffect.serializer(), text)
                }
                text.contains("\"type\":\"puzzle_start\"") -> {
                    _lastEvent.value = json.decodeFromString(ServerMessage.PuzzleStart.serializer(), text)
                }
                text.contains("\"type\":\"custom\"") -> {
                    _lastEvent.value = json.decodeFromString(ServerMessage.Custom.serializer(), text)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parseando mensaje: ${e.message}")
        }
    }

    fun sendInput(data: InputMessage): Boolean {
        return send(json.encodeToString(data))
    }

    private fun send(message: String): Boolean {
        return if (_connectionState.value == ConnectionState.CONNECTED) {
            webSocket?.send(message) ?: false
        } else {
            false
        }
    }

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR
    }
}
package com.tfg.motioncontroller.domain.repository

import android.util.Log
import com.tfg.motioncontroller.data.network.InputMessage
import com.tfg.motioncontroller.data.network.WebSocketClient
import com.tfg.motioncontroller.domain.model.ConnectionStatus
import com.tfg.motioncontroller.domain.model.SocketState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GameRepository @Inject constructor(
    private val webSocketClient: WebSocketClient
) {

    companion object {
        private const val TAG = "GameRepository"
        private const val DEFAULT_PORT = 3000
    }

    val connectionStatus: Flow<ConnectionStatus> = combine(
        webSocketClient.connectionState,
        webSocketClient.playerId,
        webSocketClient.lastEvent
    ) { socketState, playerId, lastEvent ->
        ConnectionStatus(
            socketState = when (socketState) {
                WebSocketClient.ConnectionState.DISCONNECTED -> SocketState.DISCONNECTED
                WebSocketClient.ConnectionState.CONNECTING -> SocketState.CONNECTING
                WebSocketClient.ConnectionState.CONNECTED -> SocketState.CONNECTED
                WebSocketClient.ConnectionState.ERROR -> SocketState.ERROR
            },
            playerId = playerId,
            errorMessage = if (socketState == WebSocketClient.ConnectionState.ERROR) {
                webSocketClient.getLastError()
            } else {
                null
            },
            lastEvent = lastEvent
        )
    }

    fun connect(serverIp: String, port: Int = DEFAULT_PORT) {
        val url = "ws://$serverIp:$port"
        Log.i(TAG, "Conectando a: $url")
        webSocketClient.connect(url)
    }

    fun disconnect() {
        webSocketClient.disconnect()
    }

    fun sendInput(data: InputMessage) {
        webSocketClient.sendInput(data)
    }
}
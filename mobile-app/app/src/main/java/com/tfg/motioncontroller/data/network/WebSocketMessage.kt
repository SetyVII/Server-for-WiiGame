package com.tfg.motioncontroller.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Mensaje de registro/join enviado al conectar
 */
@Serializable
data class JoinMessage(
    val type: String = "join"
)

/**
 * Mensaje de input completo enviado al servidor
 * Compatible con server.js (WiiGames)
 */
@Serializable
data class InputMessage(
    val type: String = "input",
    val gamma: Float = 0f,
    val beta: Float = 0f,
    @SerialName("dpadX") val dpadX: Int = 0,
    @SerialName("dpadY") val dpadY: Int = 0,
    @SerialName("btnA") val btnA: Boolean = false,
    @SerialName("btnB") val btnB: Boolean = false,
    @SerialName("isYelling") val isYelling: Boolean = false
)

/**
 * Mensajes recibidos del servidor
 */
@Serializable
sealed class ServerMessage {
    @Serializable
    data class AssignRole(
        val type: String = "assignRole",
        @SerialName("playerId") val playerId: Int
    ) : ServerMessage()

    @Serializable
    data class Error(
        val type: String = "error",
        val message: String
    ) : ServerMessage()

    @Serializable
    data class Collision(
        val type: String = "collision",
        @SerialName("playerId") val playerId: Int,
        @SerialName("objectName") val objectName: String? = null
    ) : ServerMessage()

    @Serializable
    data class Death(
        val type: String = "death",
        @SerialName("playerId") val playerId: Int
    ) : ServerMessage()

    @Serializable
    data class Pickup(
        val type: String = "pickup",
        @SerialName("playerId") val playerId: Int,
        @SerialName("pickupType") val pickupType: String? = null
    ) : ServerMessage()

    @Serializable
    data class UIUpdate(
        val type: String = "ui_update",
        @SerialName("playerId") val playerId: Int,
        val data: String? = null
    ) : ServerMessage()

    @Serializable
    data class ScreenEffect(
        val type: String = "screen_effect",
        @SerialName("playerId") val playerId: Int,
        val color: String? = null,
        val duration: Int? = null,
        val vibrate: Boolean? = null
    ) : ServerMessage()

    @Serializable
    data class PuzzleStart(
        val type: String = "puzzle_start",
        @SerialName("playerId") val playerId: Int,
        @SerialName("puzzleId") val puzzleId: String? = null
    ) : ServerMessage()

    @Serializable
    data class Custom(
        val type: String = "custom",
        @SerialName("playerId") val playerId: Int,
        val data: String? = null
    ) : ServerMessage()
}
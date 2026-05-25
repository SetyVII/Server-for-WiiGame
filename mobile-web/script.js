const statusEl = document.getElementById("status");
const logEl = document.getElementById("log");
const serverConfigEl = document.getElementById("server-config");
const wsAddressInput = document.getElementById("ws-address-input");
const connectBtn = document.getElementById("connect-btn");
let ws;
let myPlayerId = null; // Guardamos quién somos
let reconnectTimeout = null;
let currentWsUrl = "";

let lastSensorTime = 0;
const SENSOR_TICK_RATE = 50; // Enviar datos cada 50ms (20 FPS)

const startBtn = document.getElementById("start-sensor-btn");
const sensorGammaEl = document.getElementById("sensor-gamma");
const sensorBetaEl = document.getElementById("sensor-beta");

// Estado completo del input — se envía cada vez que algo cambia
const currentInput = {
    gamma: 0,
    beta: 0,
    dpadX: 0,
    dpadY: 0,
    btnA: false,
    btnB: false,
};

function sendCurrentInput() {
    if (!ws || ws.readyState !== WebSocket.OPEN || !myPlayerId)
        return;
    ws.send(
        JSON.stringify({
            type: "input",
            ...currentInput,
            isYelling,
        }),
    );
}

function sendPuzzleAction(puzzleId, status) {
    if (!ws || ws.readyState !== WebSocket.OPEN || !myPlayerId)
        return;
    ws.send(
        JSON.stringify({ type: "puzzle_action", puzzleId, status }),
    );
}
const micStatusEl = document.getElementById("mic-status");
const volumeBarEl = document.getElementById("volume-bar");

let isYelling = false;
let audioContext, analyser, microphone;

async function iniciarMicrofono() {
    try {
        const stream = await navigator.mediaDevices.getUserMedia({
            audio: true,
        });
        audioContext = new (
            window.AudioContext || window.webkitAudioContext
        )();
        analyser = audioContext.createAnalyser();
        analyser.fftSize = 256;
        microphone = audioContext.createMediaStreamSource(stream);
        microphone.connect(analyser);
        micStatusEl.textContent = "activo";
        micStatusEl.style.color = "#4caf50";
        medirVolumen();
    } catch (err) {
        console.error("Error al acceder al micrófono:", err);
        micStatusEl.textContent = "sin permiso";
        micStatusEl.style.color = "#f44336";
    }
}

function medirVolumen() {
    requestAnimationFrame(medirVolumen);
    const dataArray = new Uint8Array(analyser.frequencyBinCount);
    analyser.getByteFrequencyData(dataArray);

    let suma = 0;
    for (let i = 0; i < dataArray.length; i++) suma += dataArray[i];
    const volumenPromedio = suma / dataArray.length; // 0 - 255

    const umbralGrito = 20;
    isYelling = volumenPromedio > umbralGrito;

    // Actualizar barra visual (escala 0-255 a 0-100%)
    const porcentaje = Math.min((volumenPromedio / 255) * 100, 100);
    volumeBarEl.style.width = porcentaje + "%";
    volumeBarEl.classList.toggle("yelling", isYelling);
    micStatusEl.textContent = isYelling
        ? "¡GRITANDO! 📢"
        : "activo";
    micStatusEl.style.color = isYelling ? "#f44336" : "#4caf50";
}

startBtn.addEventListener("click", () => {
    if (
        typeof DeviceOrientationEvent !== "undefined" &&
        typeof DeviceOrientationEvent.requestPermission ===
            "function"
    ) {
        // Requerido en iOS 13+ y algunos Androids nuevos
        DeviceOrientationEvent.requestPermission()
            .then((permissionState) => {
                if (permissionState === "granted") {
                    iniciarSensores();
                    iniciarMicrofono();
                    startBtn.style.display = "none";
                } else {
                    alert(
                        "Permiso denegado para usar los sensores.",
                    );
                }
            })
            .catch(console.error);
    } else {
        // Dispositivos que no requieren permiso explícito (Android / PC)
        iniciarSensores();
        iniciarMicrofono();
        startBtn.style.display = "none";
    }
});

function iniciarSensores() {
    window.addEventListener("deviceorientation", (event) => {
        if (!myPlayerId) return;

        const now = Date.now();
        if (now - lastSensorTime > SENSOR_TICK_RATE) {
            const anguloMaximo = 40.0;

            let gamma = event.gamma || 0;
            if (gamma > anguloMaximo) gamma = anguloMaximo;
            if (gamma < -anguloMaximo) gamma = -anguloMaximo;
            currentInput.gamma = gamma / anguloMaximo;

            // beta: inclinación adelante/atrás, mismo rango
            let beta = event.beta || 0;
            if (beta > anguloMaximo) beta = anguloMaximo;
            if (beta < -anguloMaximo) beta = -anguloMaximo;
            currentInput.beta = beta / anguloMaximo;

            sensorGammaEl.textContent =
                currentInput.gamma.toFixed(2);
            sensorBetaEl.textContent = currentInput.beta.toFixed(2);

            sendCurrentInput();
            lastSensorTime = now;
        }
    });
}

function flashScreen(color, duration) {
    const original = document.body.style.backgroundColor;
    document.body.style.backgroundColor = color;
    setTimeout(() => {
        document.body.style.backgroundColor = original || "#1e1e1e";
    }, duration);
}

function vibrate(pattern) {
    if (navigator.vibrate) navigator.vibrate(pattern);
}

function connect() {
    if (ws) {
        try { ws.close(); } catch(e) {}
    }

    let targetUrl = wsAddressInput.value.trim();
    if (!targetUrl) {
        const protocol = window.location.protocol === "https:" ? "wss://" : "ws://";
        targetUrl = `${protocol}${window.location.host}`;
        wsAddressInput.value = targetUrl;
    }

    currentWsUrl = targetUrl;
    localStorage.setItem("wiigames_ws_url", targetUrl);

    logEl.textContent = "Conectando a " + targetUrl + "...";
    ws = new WebSocket(targetUrl);
    
    ws.onopen = () => {
        logEl.textContent = "Conectado. Esperando rol...";
        // Nada más conectar, pedimos un hueco de jugador
        ws.send(JSON.stringify({ type: "join" }));
    };

    // Escuchar mensajes del servidor (Aquí se cumple la bidireccionalidad)
    ws.onmessage = (event) => {
        const data = JSON.parse(event.data);

        // ── Manejo de errores ─────────────────────────────────────────
        if (data.type === "error") {
            logEl.textContent = `❌ Error: ${data.message}`;
            statusEl.textContent = `❌ ${data.message}`;
            statusEl.style.color = "#f44336";
            if (reconnectTimeout) clearTimeout(reconnectTimeout);
            return;
        }

        // ── Asignación de rol ─────────────────────────────────────────
        if (data.type === "assignRole") {
            myPlayerId = data.playerId;
            serverConfigEl.style.display = "none"; // Ocultar configuración al conectar con éxito
            if (myPlayerId === 1) {
                statusEl.textContent =
                    "🟢 Eres el Jugador 1 (Azul)";
                statusEl.style.color = "#00a8ff";
                document.body.style.border = "5px solid #00a8ff";
            } else if (myPlayerId === 2) {
                statusEl.textContent =
                    "🟢 Eres el Jugador 2 (Rojo)";
                statusEl.style.color = "#e84118";
                document.body.style.border = "5px solid #e84118";
            }
            return;
        }

        // ── Eventos de Unity ──────────────────────────────────────────
        if (data.type === "collision") {
            logEl.textContent = `💥 Choque con ${data.objectName || "objeto"}`;
            flashScreen("#ff0000", 150);
            vibrate(1001);
            return;
        }

        if (data.type === "death") {
            logEl.textContent = `💀 ¡Has muerto!`;
            flashScreen("#ff0000", 400);
            vibrate([500, 100, 401]);
            return;
        }

        if (data.type === "pickup") {
            logEl.textContent = `✨ Recogiste: ${data.pickupType || "item"}`;
            flashScreen("#00ff88", 150);
            vibrate(1001);
            return;
        }

        if (data.type === "ui_update") {
            logEl.textContent = `🖥️ ${data.data || JSON.stringify(data)}`;
            return;
        }

        if (data.type === "screen_effect") {
            flashScreen(
                data.color || "#ffffff",
                data.duration || 200,
            );
            if (data.vibrate)
                vibrate(Math.max(data.duration || 150, 1001));
            return;
        }

        if (data.type === "puzzle_start") {
            logEl.textContent = `🧩 Puzzle: ${data.puzzleId}`;
            vibrate(1001);
            return;
        }

        if (data.type === "custom") {
            logEl.textContent = `📨 ${data.data || JSON.stringify(data)}`;
            vibrate(1001);
            return;
        }
    };

    ws.onclose = () => {
        statusEl.textContent = "🔴 Desconectado (Reintentando...)";
        statusEl.style.color = "#f44336";
        myPlayerId = null;
        document.body.style.border = "none";
        serverConfigEl.style.display = "flex"; // Volver a mostrar la configuración al desconectar
        
        if (reconnectTimeout) clearTimeout(reconnectTimeout);
        reconnectTimeout = setTimeout(connect, 2000);
    };
}

// sendInput ya no se usa directamente; los handlers usan currentInput + sendCurrentInput()

document.querySelectorAll(".d-pad button").forEach((btn) => {
    const x = parseFloat(btn.getAttribute("data-x"));
    const y = parseFloat(btn.getAttribute("data-y"));
    btn.addEventListener("pointerdown", () => {
        currentInput.dpadX = x;
        currentInput.dpadY = y;
        sendCurrentInput();
    });
    btn.addEventListener("pointerup", () => {
        currentInput.dpadX = 0;
        currentInput.dpadY = 0;
        sendCurrentInput();
    });
    btn.addEventListener("pointerleave", () => {
        currentInput.dpadX = 0;
        currentInput.dpadY = 0;
        sendCurrentInput();
    });
});

// Botón A (salto)
document
    .querySelector(".btn-a")
    .addEventListener("pointerdown", () => {
        currentInput.btnA = true;
        sendCurrentInput();
    });
document
    .querySelector(".btn-a")
    .addEventListener("pointerup", () => {
        currentInput.btnA = false;
        sendCurrentInput();
    });
document
    .querySelector(".btn-a")
    .addEventListener("pointerleave", () => {
        currentInput.btnA = false;
        sendCurrentInput();
    });

// Botón B (acción secundaria)
document
    .querySelector(".btn-b")
    .addEventListener("pointerdown", () => {
        currentInput.btnB = true;
        sendCurrentInput();
    });
document
    .querySelector(".btn-b")
    .addEventListener("pointerup", () => {
        currentInput.btnB = false;
        sendCurrentInput();
    });
document
    .querySelector(".btn-b")
    .addEventListener("pointerleave", () => {
        currentInput.btnB = false;
        sendCurrentInput();
    });

// Inicializar valor de input de manera dinámica y robusta
let defaultUrl = "";
if (window.location.protocol.startsWith("http")) {
    // Si se sirve desde un servidor web (Javalin, ngrok, etc.), usar el mismo host y protocolo
    const protocol = window.location.protocol === "https:" ? "wss://" : "ws://";
    defaultUrl = `${protocol}${window.location.host}`;
} else {
    // Si se abre localmente como archivo
    const savedUrl = localStorage.getItem("wiigames_ws_url");
    defaultUrl = savedUrl || "ws://localhost:3002";
}
wsAddressInput.value = defaultUrl;

connectBtn.addEventListener("click", () => {
    if (reconnectTimeout) clearTimeout(reconnectTimeout);
    connect();
});

connect();
// ============================================
// ESTADO GLOBAL
// ============================================
const AppState = {
    currentScreen: 'connection',
    ws: null,
    myPlayerId: null,
    reconnectTimeout: null,
    currentWsUrl: '',
    
    // Sensores
    sensorsActive: false,
    sensorOffset: { gamma: 0, beta: 0 },
    isCalibrating: false,
    calibrationProgress: 0,
    lastSensorTime: 0,
    SENSOR_TICK_RATE: 50,
    
    // Input
    currentInput: {
        type: 'input',
        gamma: 0,
        beta: 0,
        dpadX: 0,
        dpadY: 0,
        btnA: false,
        btnB: false,
        isYelling: false,
    },
    
    // Micrófono
    isYelling: false,
    audioContext: null,
    analyser: null,
    microphone: null,
    micActive: false,
    
    // Settings
    settings: {
        darkMode: true,
        controlMode: 'touchpad', // 'touchpad' | 'buttons'
        orientation: 'landscape', // 'landscape' | 'portrait'
        sensitivity: 'medium', // 'low' | 'medium' | 'high' | 'custom'
        customForce: 45,
    },
    
    // Touchpad
    isDragging: false,
    manualOffset: { x: 0, y: 0 },
};

// ============================================
// NAVEGACIÓN ENTRE PANTALLAS
// ============================================
function showScreen(screenName) {
    // Ocultar todas las pantallas
    document.querySelectorAll('.screen').forEach(screen => {
        screen.classList.remove('active');
    });
    
    // Mostrar la pantalla solicitada
    const targetScreen = document.getElementById(`screen-${screenName}`);
    if (targetScreen) {
        targetScreen.classList.add('active');
        AppState.currentScreen = screenName;
    }
}

// ============================================
// PANTALLA 1: CONTROLLER
// ============================================
const controllerScreen = {
    elements: {},
    
    init() {
        this.cacheElements();
        this.bindEvents();
        this.updateUI();
    },
    
    autoConnect(url) {
        AppState.currentWsUrl = url;
        
        if (AppState.ws) {
            try { AppState.ws.close(); } catch(e) {}
        }
        
        AppState.ws = new WebSocket(url);
        
        AppState.ws.onopen = () => {
            this.logEvent('Conectado. Esperando rol...');
            AppState.ws.send(JSON.stringify({ type: 'join' }));
        };
        
        AppState.ws.onmessage = (event) => {
            const data = JSON.parse(event.data);
            this.handleServerMessage(data);
        };
        
        AppState.ws.onerror = () => {
            this.logEvent('Error de conexión');
            this.updateUI();
        };
        
        AppState.ws.onclose = () => {
            this.handleDisconnect();
        };
        
        this.updateUI();
    },
    
    cacheElements() {
        this.elements = {
            socketStatus: document.getElementById('socket-status'),
            playerStatus: document.getElementById('player-status'),
            toggleSensorsBtn: document.getElementById('toggle-sensors-btn'),
            toggleMicBtn: document.getElementById('toggle-mic-btn'),
            micIcon: document.getElementById('mic-icon'),
            testVibrationBtn: document.getElementById('test-vibration-btn'),
            openSettingsBtn: document.getElementById('open-settings-btn'),
            disconnectBtn: document.getElementById('disconnect-btn'),
            calibrationPanel: document.getElementById('calibration-panel'),
            calibrationProgress: document.getElementById('calibration-progress'),
            calibrationPercent: document.getElementById('calibration-percent'),
            debugInfo: document.getElementById('debug-info'),
            debugTiltX: document.getElementById('debug-tilt-x'),
            debugTiltY: document.getElementById('debug-tilt-y'),
            micPanel: document.getElementById('mic-panel'),
            micVolumeBar: document.getElementById('mic-volume-bar'),
            micRms: document.getElementById('mic-rms'),
            blowIndicator: document.getElementById('blow-indicator'),
            thresholdSlider: document.getElementById('threshold-slider'),
            thresholdValue: document.getElementById('threshold-value'),
            cooldownSlider: document.getElementById('cooldown-slider'),
            cooldownValue: document.getElementById('cooldown-value'),
            scaleSlider: document.getElementById('scale-slider'),
            scaleValue: document.getElementById('scale-value'),
            touchpadMode: document.getElementById('touchpad-mode'),
            buttonsMode: document.getElementById('buttons-mode'),
            touchpad: document.getElementById('touchpad'),
            touchpadDot: document.getElementById('touchpad-dot'),
            eventLog: document.getElementById('event-log'),
            errorMessage: document.getElementById('error-message'),
            controllerMain: document.querySelector('.controller-main'),
        };
    },
    
    bindEvents() {
        // Sensores
        this.elements.toggleSensorsBtn.addEventListener('click', () => {
            if (AppState.sensorsActive) {
                this.stopSensors();
            } else {
                this.startSensors();
            }
        });
        
        // Micrófono
        this.elements.toggleMicBtn.addEventListener('click', () => {
            if (AppState.micActive) {
                this.stopMicrophone();
            } else {
                this.startMicrophone();
            }
        });
        
        // Test vibración
        this.elements.testVibrationBtn.addEventListener('click', () => {
            this.testVibration();
        });
        
        // Settings
        this.elements.openSettingsBtn.addEventListener('click', () => {
            showScreen('settings');
        });
        
        // Desconectar
        this.elements.disconnectBtn.addEventListener('click', () => {
            this.disconnect();
        });
        
        // Touchpad
        this.bindTouchpadEvents();
        
        // Botones D-Pad
        this.bindDPadEvents();
        
        // Botones A/B
        this.bindActionButtons();
        
        // Sliders del micrófono
        this.bindMicSliders();
        
        // WebSocket events
        if (AppState.ws) {
            AppState.ws.onmessage = (event) => {
                const data = JSON.parse(event.data);
                this.handleServerMessage(data);
            };
            
            AppState.ws.onclose = () => {
                this.handleDisconnect();
            };
        }
        
        // Actualizar modo de control
        this.updateControlMode();
        
        // Actualizar orientación
        this.updateOrientation();
    },
    
    bindTouchpadEvents() {
        const touchpad = this.elements.touchpad;
        let maxRadiusX, maxRadiusY;
        
        const updateDimensions = () => {
            const rect = touchpad.getBoundingClientRect();
            maxRadiusX = rect.width / 2 * 0.75;
            maxRadiusY = rect.height / 2 * 0.75;
        };
        
        const handleStart = (e) => {
            if (AppState.sensorsActive) return;
            e.preventDefault();
            AppState.isDragging = true;
            updateDimensions();
            
            const rect = touchpad.getBoundingClientRect();
            const clientX = e.touches ? e.touches[0].clientX : e.clientX;
            const clientY = e.touches ? e.touches[0].clientY : e.clientY;
            
            AppState.manualOffset.x = clientX - rect.left - rect.width / 2;
            AppState.manualOffset.y = clientY - rect.top - rect.height / 2;
            
            this.clampAndUpdateTouchpad();
        };
        
        const handleMove = (e) => {
            if (!AppState.isDragging) return;
            e.preventDefault();
            
            const rect = touchpad.getBoundingClientRect();
            const clientX = e.touches ? e.touches[0].clientX : e.clientX;
            const clientY = e.touches ? e.touches[0].clientY : e.clientY;
            
            AppState.manualOffset.x = clientX - rect.left - rect.width / 2;
            AppState.manualOffset.y = clientY - rect.top - rect.height / 2;
            
            this.clampAndUpdateTouchpad();
        };
        
        const handleEnd = () => {
            AppState.isDragging = false;
            AppState.manualOffset = { x: 0, y: 0 };
            AppState.currentInput.gamma = 0;
            AppState.currentInput.beta = 0;
            this.sendInput();
            this.updateTouchpadVisual();
        };
        
        touchpad.addEventListener('pointerdown', handleStart);
        touchpad.addEventListener('pointermove', handleMove);
        touchpad.addEventListener('pointerup', handleEnd);
        touchpad.addEventListener('pointerleave', handleEnd);
        touchpad.addEventListener('touchstart', handleStart, { passive: false });
        touchpad.addEventListener('touchmove', handleMove, { passive: false });
        touchpad.addEventListener('touchend', handleEnd);
    },
    
    clampAndUpdateTouchpad() {
        const touchpad = this.elements.touchpad;
        const rect = touchpad.getBoundingClientRect();
        const maxRadiusX = rect.width / 2 * 0.75;
        const maxRadiusY = rect.height / 2 * 0.75;
        
        let { x, y } = AppState.manualOffset;
        
        const normalizedX = x / maxRadiusX;
        const normalizedY = y / maxRadiusY;
        const dist = Math.sqrt(normalizedX * normalizedX + normalizedY * normalizedY);
        
        if (dist > 1) {
            x = x / dist;
            y = y / dist;
            AppState.manualOffset = { x, y };
        }
        
        const gamma = (x / maxRadiusX).clamp(-1, 1);
        const beta = -(y / maxRadiusY).clamp(-1, 1);
        
        AppState.currentInput.gamma = gamma;
        AppState.currentInput.beta = beta;
        this.sendInput();
        this.updateTouchpadVisual();
    },
    
    updateTouchpadVisual() {
        const dot = this.elements.touchpadDot;
        
        if (AppState.sensorsActive) {
            // Usar valores de sensores
            const gamma = AppState.currentInput.gamma;
            const beta = AppState.currentInput.beta;
            const x = gamma * 100; // %
            const y = -beta * 100; // %
            dot.style.transform = `translate(${x}%, ${y}%)`;
        } else if (AppState.isDragging) {
            // Usar offset manual
            const touchpad = this.elements.touchpad;
            const rect = touchpad.getBoundingClientRect();
            const x = (AppState.manualOffset.x / (rect.width / 2)) * 100;
            const y = (AppState.manualOffset.y / (rect.height / 2)) * 100;
            dot.style.transform = `translate(${x}%, ${y}%)`;
        } else {
            dot.style.transform = 'translate(0, 0)';
        }
    },
    
    bindDPadEvents() {
        const buttons = document.querySelectorAll('.d-pad button');
        
        buttons.forEach(btn => {
            const x = parseFloat(btn.getAttribute('data-x'));
            const y = parseFloat(btn.getAttribute('data-y'));
            
            const handlePress = () => {
                if (AppState.sensorsActive) return;
                AppState.currentInput.dpadX = x;
                AppState.currentInput.dpadY = y;
                this.sendInput();
            };
            
            const handleRelease = () => {
                AppState.currentInput.dpadX = 0;
                AppState.currentInput.dpadY = 0;
                this.sendInput();
            };
            
            btn.addEventListener('pointerdown', handlePress);
            btn.addEventListener('pointerup', handleRelease);
            btn.addEventListener('pointerleave', handleRelease);
        });
    },
    
    bindActionButtons() {
        const btnA = document.querySelector('.btn-action.btn-a');
        const btnB = document.querySelector('.btn-action.btn-b');
        
        const bindButton = (btn, key) => {
            const handlePress = () => {
                AppState.currentInput[key] = true;
                this.sendInput();
            };
            const handleRelease = () => {
                AppState.currentInput[key] = false;
                this.sendInput();
            };
            
            btn.addEventListener('pointerdown', handlePress);
            btn.addEventListener('pointerup', handleRelease);
            btn.addEventListener('pointerleave', handleRelease);
        };
        
        bindButton(btnA, 'btnA');
        bindButton(btnB, 'btnB');
    },
    
    bindMicSliders() {
        this.elements.thresholdSlider.addEventListener('input', (e) => {
            const val = e.target.value / 100;
            this.elements.thresholdValue.textContent = val.toFixed(2);
        });
        
        this.elements.cooldownSlider.addEventListener('input', (e) => {
            this.elements.cooldownValue.textContent = `${e.target.value}ms`;
        });
        
        this.elements.scaleSlider.addEventListener('input', (e) => {
            this.elements.scaleValue.textContent = `${parseFloat(e.target.value).toFixed(1)}x`;
        });
    },
    
    startSensors() {
        if (typeof DeviceOrientationEvent === 'undefined') {
            this.showError('Error al enviar datos de sensores.');
            return;
        }
        
        // Pedir permiso en iOS 13+
        if (typeof DeviceOrientationEvent.requestPermission === 'function') {
            DeviceOrientationEvent.requestPermission()
                .then(permissionState => {
                    if (permissionState === 'granted') {
                        this.activateSensors();
                    } else {
                        this.showError('Permiso denegado para usar los sensores');
                    }
                })
                .catch(err => {
                    console.error('Error solicitando permiso:', err);
                    this.showError('Error al acceder a los sensores');
                });
        } else {
            this.activateSensors();
        }
    },
    
    activateSensors() {
        AppState.sensorsActive = true;
        AppState.isCalibrating = true;
        AppState.calibrationProgress = 0;
        
        // Mostrar panel de calibración
        this.elements.calibrationPanel.classList.remove('hidden');
        this.elements.debugInfo.classList.remove('hidden');
        
        // Tomar posición actual como centro
        const handleOrientation = (event) => {
            if (!AppState.sensorsActive) return;
            
            const now = Date.now();
            if (now - AppState.lastSensorTime < AppState.SENSOR_TICK_RATE) return;
            AppState.lastSensorTime = now;
            
            const gamma = event.gamma || 0;
            const beta = event.beta || 0;
            
            if (AppState.isCalibrating) {
                // Primera lectura = centro
                AppState.sensorOffset = { gamma, beta };
                AppState.isCalibrating = false;
                this.elements.calibrationPanel.classList.add('hidden');
            }
            
            // Aplicar offset
            const calibratedGamma = gamma - AppState.sensorOffset.gamma;
            const calibratedBeta = beta - AppState.sensorOffset.beta;
            
            // Mapear a [-1, 1]
            const maxAngle = 40;
            const mappedGamma = (calibratedGamma / maxAngle).clamp(-1, 1);
            const mappedBeta = (calibratedBeta / maxAngle).clamp(-1, 1);
            
            AppState.currentInput.gamma = mappedGamma;
            AppState.currentInput.beta = mappedBeta;
            
            // Actualizar debug
            this.elements.debugTiltX.textContent = mappedGamma.toFixed(2);
            this.elements.debugTiltY.textContent = mappedBeta.toFixed(2);
            
            // Actualizar visual del touchpad
            this.updateTouchpadVisual();
            
            // Enviar input
            this.sendInput();
        };
        
        window.addEventListener('deviceorientation', handleOrientation);
        this._orientationHandler = handleOrientation;
        
        this.updateUI();
    },
    
    stopSensors() {
        AppState.sensorsActive = false;
        AppState.isCalibrating = false;
        
        if (this._orientationHandler) {
            window.removeEventListener('deviceorientation', this._orientationHandler);
            this._orientationHandler = null;
        }
        
        AppState.currentInput.gamma = 0;
        AppState.currentInput.beta = 0;
        this.sendInput();
        
        this.elements.calibrationPanel.classList.add('hidden');
        this.elements.debugInfo.classList.add('hidden');
        this.updateUI();
    },
    
    startMicrophone() {
        navigator.mediaDevices.getUserMedia({ audio: true })
            .then(stream => {
                AppState.audioContext = new (window.AudioContext || window.webkitAudioContext)();
                AppState.analyser = AppState.audioContext.createAnalyser();
                AppState.analyser.fftSize = 256;
                AppState.microphone = AppState.audioContext.createMediaStreamSource(stream);
                AppState.microphone.connect(AppState.analyser);
                
                AppState.micActive = true;
                this.elements.micPanel.classList.remove('hidden');
                this.elements.micIcon.textContent = '🎙️';
                this.elements.toggleMicBtn.style.color = '#EF4444';
                
                this.measureVolume();
            })
            .catch(err => {
                console.error('Error al acceder al micrófono:', err);
                this.showError('Error al acceder al micrófono');
            });
    },
    
    stopMicrophone() {
        AppState.micActive = false;
        if (AppState.audioContext) {
            AppState.audioContext.close();
            AppState.audioContext = null;
        }
        this.elements.micPanel.classList.add('hidden');
        this.elements.micIcon.textContent = '🎙️';
        this.elements.toggleMicBtn.style.color = '';
        AppState.currentInput.isYelling = false;
        this.sendInput();
    },
    
    measureVolume() {
        if (!AppState.micActive || !AppState.analyser) return;
        
        requestAnimationFrame(() => this.measureVolume());
        
        const dataArray = new Uint8Array(AppState.analyser.frequencyBinCount);
        AppState.analyser.getByteFrequencyData(dataArray);
        
        let sum = 0;
        for (let i = 0; i < dataArray.length; i++) sum += dataArray[i];
        const average = sum / dataArray.length;
        
        const threshold = parseInt(this.elements.thresholdSlider.value);
        const scale = parseFloat(this.elements.scaleSlider.value);
        const progress = Math.min((average / 255) * 100 * scale / 3.33, 100);
        
        // Actualizar barra visual
        this.elements.micVolumeBar.style.width = `${progress}%`;
        this.elements.micRms.textContent = `${Math.round(progress)}%`;
        
        // Color según intensidad
        this.elements.micVolumeBar.classList.remove('medium', 'high');
        if (progress > 80) {
            this.elements.micVolumeBar.classList.add('high');
        } else if (progress > 50) {
            this.elements.micVolumeBar.classList.add('medium');
        }
        
        // Detectar "soplo"
        const isBlowing = average > threshold;
        if (isBlowing !== AppState.isYelling) {
            AppState.isYelling = isBlowing;
            AppState.currentInput.isYelling = isBlowing;
            this.sendInput();
        }
        
        if (isBlowing) {
            this.elements.blowIndicator.classList.remove('hidden');
        } else {
            this.elements.blowIndicator.classList.add('hidden');
        }
    },
    
    testVibration() {
        if (navigator.vibrate) {
            navigator.vibrate([0, 1000, 500, 100, 50, 100, 50, 100, 50, 100, 500, 1000]);
        }
    },
    
    handleServerMessage(data) {
        switch (data.type) {
            case 'assignRole':
                AppState.myPlayerId = data.playerId;
                this.logEvent(`Asignado como Jugador ${data.playerId}`);
                this.updateUI();
                break;
            case 'collision':
                this.logEvent('💥 Choque!');
                this.flashScreen('#ff0000', 150);
                this.vibrate(100);
                break;
            case 'death':
                this.logEvent('💀 Has muerto!');
                this.flashScreen('#ff0000', 400);
                this.vibrate([0, 500, 200, 500]);
                break;
            case 'pickup':
                this.logEvent('✨ Objeto recogido!');
                this.flashScreen('#00ff88', 150);
                this.vibrate(50);
                break;
            case 'ui_update':
                this.logEvent(`🖥️ ${data.data || 'Update'}`);
                break;
            case 'screen_effect':
                this.flashScreen(data.color || '#ffffff', data.duration || 200);
                if (data.vibrate) this.vibrate(Math.max(data.duration || 150, 100));
                break;
            case 'puzzle_start':
                this.logEvent(`🧩 Puzzle: ${data.puzzleId}`);
                this.vibrate(100);
                break;
            case 'custom':
                this.logEvent(`📨 ${data.data || 'Evento'}`);
                this.vibrate(100);
                break;
            case 'error':
                this.showError(data.message);
                break;
        }
    },
    
    handleDisconnect() {
        AppState.myPlayerId = null;
        this.elements.socketStatus.textContent = 'Socket: desconectado';
        this.elements.socketStatus.classList.remove('connected');
        this.elements.playerStatus.textContent = 'Sin rol';
        this.elements.playerStatus.className = 'player-status';
        this.updateUI();
        
        // Auto-reconectar después de 2 segundos
        if (AppState.reconnectTimeout) clearTimeout(AppState.reconnectTimeout);
        AppState.reconnectTimeout = setTimeout(() => {
            if (!AppState.ws || AppState.ws.readyState !== WebSocket.OPEN) {
                this.logEvent('Reconectando...');
                this.autoConnect(AppState.currentWsUrl);
            }
        }, 2000);
    },
    
    disconnect() {
        AppState.sensorsActive = false;
        AppState.micActive = false;
        
        if (this._orientationHandler) {
            window.removeEventListener('deviceorientation', this._orientationHandler);
        }
        
        if (AppState.ws) {
            AppState.ws.close();
            AppState.ws = null;
        }
        
        if (AppState.reconnectTimeout) {
            clearTimeout(AppState.reconnectTimeout);
            AppState.reconnectTimeout = null;
        }
        
        this.updateUI();
        this.logEvent('Desconectado');
        
        // Auto-reconectar después de 2 segundos
        AppState.reconnectTimeout = setTimeout(() => {
            if (!AppState.ws || AppState.ws.readyState !== WebSocket.OPEN) {
                this.logEvent('Reconectando...');
                this.autoConnect(AppState.currentWsUrl);
            }
        }, 2000);
    },
    
    sendInput() {
        if (!AppState.ws || AppState.ws.readyState !== WebSocket.OPEN || !AppState.myPlayerId) return;
        
        const input = {
            ...AppState.currentInput,
            isYelling: AppState.isYelling,
        };
        
        AppState.ws.send(JSON.stringify(input));
    },
    
    updateUI() {
        // Estado del socket
        if (AppState.ws && AppState.ws.readyState === WebSocket.OPEN) {
            this.elements.socketStatus.textContent = 'Socket: conectado';
            this.elements.socketStatus.classList.add('connected');
        } else {
            this.elements.socketStatus.textContent = 'Socket: desconectado';
            this.elements.socketStatus.classList.remove('connected');
        }
        
        // Estado del jugador
        if (AppState.myPlayerId) {
            this.elements.playerStatus.textContent = `Jugador ${AppState.myPlayerId}`;
            this.elements.playerStatus.className = `player-status player-${AppState.myPlayerId}`;
        } else {
            this.elements.playerStatus.textContent = 'Sin rol';
            this.elements.playerStatus.className = 'player-status';
        }
        
        // Botón de sensores
        if (AppState.sensorsActive) {
            this.elements.toggleSensorsBtn.textContent = 'Desactivar sensores';
            this.elements.toggleSensorsBtn.classList.add('active');
            this.elements.touchpad.classList.add('disabled');
        } else {
            this.elements.toggleSensorsBtn.textContent = 'Activar sensores';
            this.elements.toggleSensorsBtn.classList.remove('active');
            this.elements.touchpad.classList.remove('disabled');
        }
    },
    
    updateControlMode() {
        const mode = AppState.settings.controlMode;
        if (mode === 'touchpad') {
            this.elements.touchpadMode.classList.remove('hidden');
            this.elements.buttonsMode.classList.add('hidden');
        } else {
            this.elements.touchpadMode.classList.add('hidden');
            this.elements.buttonsMode.classList.remove('hidden');
        }
    },
    
    updateOrientation() {
        const orientation = AppState.settings.orientation;
        if (orientation === 'portrait') {
            this.elements.controllerMain.classList.add('portrait-layout');
        } else {
            this.elements.controllerMain.classList.remove('portrait-layout');
        }
    },
    
    logEvent(message) {
        this.elements.eventLog.textContent = message;
        setTimeout(() => {
            if (this.elements.eventLog.textContent === message) {
                this.elements.eventLog.textContent = '';
            }
        }, 3000);
    },
    
    showError(message) {
        this.elements.errorMessage.textContent = message;
        this.elements.errorMessage.classList.remove('hidden');
        setTimeout(() => {
            this.elements.errorMessage.classList.add('hidden');
        }, 5000);
    },
    
    flashScreen(color, duration) {
        const original = document.body.style.backgroundColor;
        document.body.style.backgroundColor = color;
        setTimeout(() => {
            document.body.style.backgroundColor = original || '#1A1A2E';
        }, duration);
    },
    
    vibrate(pattern) {
        if (navigator.vibrate) navigator.vibrate(pattern);
    }
};

// ============================================
// PANTALLA 3: SETTINGS
// ============================================
const settingsScreen = {
    init() {
        this.bindEvents();
        this.loadSettings();
    },
    
    bindEvents() {
        // Volver
        document.getElementById('back-btn').addEventListener('click', () => {
            showScreen('controller');
        });
        
        // Tema
        document.getElementById('theme-light').addEventListener('click', () => {
            this.setTheme(false);
        });
        document.getElementById('theme-dark').addEventListener('click', () => {
            this.setTheme(true);
        });
        
        // Modo de control
        document.getElementById('mode-touchpad').addEventListener('click', () => {
            this.setControlMode('touchpad');
        });
        document.getElementById('mode-buttons').addEventListener('click', () => {
            this.setControlMode('buttons');
        });
        
        // Orientación
        document.getElementById('orientation-landscape').addEventListener('click', () => {
            this.setOrientation('landscape');
        });
        document.getElementById('orientation-portrait').addEventListener('click', () => {
            this.setOrientation('portrait');
        });
        
        // Sensibilidad
        document.querySelectorAll('.sensitivity-btn').forEach(btn => {
            btn.addEventListener('click', () => {
                const level = btn.getAttribute('data-level');
                this.setSensitivity(level);
            });
        });
        
        // Custom force
        document.getElementById('custom-force-input').addEventListener('input', (e) => {
            AppState.settings.customForce = parseInt(e.target.value) || 45;
            this.updateSensitivityDisplay();
        });
    },
    
    loadSettings() {
        const saved = localStorage.getItem('wiicell_settings');
        if (saved) {
            AppState.settings = { ...AppState.settings, ...JSON.parse(saved) };
        }
        
        // Aplicar settings UI
        this.updateThemeUI();
        this.updateControlModeUI();
        this.updateOrientationUI();
        this.updateSensitivityUI();
    },
    
    saveSettings() {
        localStorage.setItem('wiicell_settings', JSON.stringify(AppState.settings));
    },
    
    setTheme(dark) {
        AppState.settings.darkMode = dark;
        this.updateThemeUI();
        this.saveSettings();
    },
    
    updateThemeUI() {
        const isDark = AppState.settings.darkMode;
        document.getElementById('theme-light').classList.toggle('active', !isDark);
        document.getElementById('theme-dark').classList.toggle('active', isDark);
    },
    
    setControlMode(mode) {
        AppState.settings.controlMode = mode;
        this.updateControlModeUI();
        this.saveSettings();
        
        // Actualizar controller si está visible
        if (AppState.currentScreen === 'controller') {
            controllerScreen.updateControlMode();
        }
    },
    
    updateControlModeUI() {
        const mode = AppState.settings.controlMode;
        document.getElementById('mode-touchpad').classList.toggle('active', mode === 'touchpad');
        document.getElementById('mode-buttons').classList.toggle('active', mode === 'buttons');
    },
    
    setOrientation(orientation) {
        AppState.settings.orientation = orientation;
        this.updateOrientationUI();
        this.saveSettings();
        
        // Actualizar controller si está visible
        if (AppState.currentScreen === 'controller') {
            controllerScreen.updateOrientation();
        }
    },
    
    updateOrientationUI() {
        const orientation = AppState.settings.orientation;
        document.getElementById('orientation-landscape').classList.toggle('active', orientation === 'landscape');
        document.getElementById('orientation-portrait').classList.toggle('active', orientation === 'portrait');
    },
    
    setSensitivity(level) {
        AppState.settings.sensitivity = level;
        this.updateSensitivityUI();
        this.saveSettings();
    },
    
    updateSensitivityUI() {
        const level = AppState.settings.sensitivity;
        
        document.querySelectorAll('.sensitivity-btn').forEach(btn => {
            btn.classList.toggle('active', btn.getAttribute('data-level') === level);
        });
        
        // Mostrar/ocultar custom force
        const customContainer = document.getElementById('custom-force-container');
        customContainer.classList.toggle('hidden', level !== 'custom');
        
        this.updateSensitivityDisplay();
    },
    
    updateSensitivityDisplay() {
        const level = AppState.settings.sensitivity;
        const forceMap = {
            low: { label: 'Bajo', force: 0.8 },
            medium: { label: 'Medio', force: 4.5 },
            high: { label: 'Alto', force: 10.0 },
            custom: { label: 'Custom', force: AppState.settings.customForce / 10 }
        };
        
        const info = forceMap[level];
        document.getElementById('current-level').textContent = info.label;
        document.getElementById('current-force').textContent = info.force.toFixed(1);
    }
};

// ============================================
// UTILIDADES
// ============================================
Number.prototype.clamp = function(min, max) {
    return Math.min(Math.max(this, min), max);
};

// ============================================
// INICIALIZACIÓN
// ============================================
document.addEventListener('DOMContentLoaded', () => {
    // Inicializar pantallas
    controllerScreen.init();
    settingsScreen.init();
    
    // Mostrar pantalla de controller directamente (en web el servidor ya sirve esta página)
    showScreen('controller');
    
    // Conectar automáticamente al WebSocket del servidor actual
    const protocol = window.location.protocol === 'https:' ? 'wss://' : 'ws://';
    const wsUrl = `${protocol}${window.location.host}`;
    controllerScreen.autoConnect(wsUrl);
    
    // Cargar settings
    settingsScreen.loadSettings();
});
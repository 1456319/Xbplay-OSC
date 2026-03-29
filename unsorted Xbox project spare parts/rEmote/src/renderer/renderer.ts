/**
 * rEmote - Renderer Process
 * Main UI logic
 */

// UI Elements
const connectBtn = document.getElementById('connectBtn') as HTMLButtonElement;
const statusText = document.getElementById('status') as HTMLDivElement;
const videoContainer = document.getElementById('videoContainer') as HTMLDivElement;
const remoteVideo = document.getElementById('remoteVideo') as HTMLVideoElement;

let isConnected = false;

// Update status display
function updateStatus(message: string) {
  statusText.textContent = message;
  console.log('[rEmote]', message);
}

// Connect button handler
connectBtn.addEventListener('click', async () => {
  if (isConnected) {
    await disconnect();
  } else {
    await connect();
  }
});

// Connect to Xbox
async function connect() {
  try {
    connectBtn.disabled = true;
    updateStatus('Authenticating...');

    // Step 1: Authenticate (OAuth → XSTS)
    // TODO: Implement auth flow from spec 01_REMOTE_PLAY_AUTHENTICATION_FLOW.md
    await window.remote.auth.login();
    updateStatus('Creating session...');

    // Step 2: Create session
    // TODO: Implement session creation from spec 02_WEBRTC_SESSION_PROTOCOL.md
    const consoleId = 'CONSOLE_ID_HERE'; // TODO: Console discovery
    await window.remote.session.create(consoleId);
    updateStatus('Establishing connection...');

    // Step 3: WebRTC setup happens in main process
    // TODO: Implement WebRTC signaling from spec 02_WEBRTC_SESSION_PROTOCOL.md

    // Step 4: Wait for video stream
    // TODO: Attach remote stream to video element

    updateStatus('Connected!');
    isConnected = true;
    connectBtn.textContent = 'Disconnect';
    connectBtn.disabled = false;

    // Show video container
    videoContainer.style.display = 'block';

  } catch (error) {
    console.error('Connection failed:', error);
    updateStatus(`Connection failed: ${error.message}`);
    connectBtn.disabled = false;
  }
}

// Disconnect from Xbox
async function disconnect() {
  try {
    updateStatus('Disconnecting...');
    await window.remote.session.close();
    
    isConnected = false;
    connectBtn.textContent = 'Connect to Xbox';
    videoContainer.style.display = 'none';
    updateStatus('Disconnected');

  } catch (error) {
    console.error('Disconnect failed:', error);
    updateStatus(`Disconnect failed: ${error.message}`);
  }
}

// Handle gamepad input
// TODO: Implement input protocol from spec 05_INPUT_PROTOCOL_SPECIFICATION.md
let gamepadInterval: number | null = null;

function startGamepadPolling() {
  gamepadInterval = window.setInterval(() => {
    const gamepads = navigator.getGamepads();
    for (const gamepad of gamepads) {
      if (gamepad) {
        // TODO: Convert gamepad state to 43-byte binary message
        // See spec 05_INPUT_PROTOCOL_SPECIFICATION.md for format
        const inputState = {
          buttons: gamepad.buttons.map(b => b.pressed),
          axes: gamepad.axes,
        };
        window.remote.input.sendGamepad(inputState);
      }
    }
  }, 16); // ~60 Hz
}

function stopGamepadPolling() {
  if (gamepadInterval) {
    clearInterval(gamepadInterval);
    gamepadInterval = null;
  }
}

// Listen for events from main process
window.remote.on('session:status', (status: any) => {
  updateStatus(status.message);
});

window.remote.on('error', (error: any) => {
  console.error('Error from main:', error);
  updateStatus(`Error: ${error.message}`);
});

// Initialize
updateStatus('Ready to connect');
console.log('[rEmote] Renderer initialized');
console.log('[rEmote] Implementation TODO:');
console.log('  - Authentication (spec 01)');
console.log('  - Session creation (spec 02)');
console.log('  - WebRTC signaling (spec 02)');
console.log('  - DataChannels (spec 03)');
console.log('  - Video/Audio (spec 04)');
console.log('  - Input protocol (spec 05)');
console.log('[rEmote] See docs/specs/ for complete protocol documentation');

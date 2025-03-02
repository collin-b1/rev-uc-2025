# B.A.S.S.
Button Activated Switch System

Handles key and button user interaction that serially communicates with BassGPT (Java central system) for signaling which physical component the user interacted with.

## Key
Switching the key on enables an LED and signals to BassGPT to display a GUI system for text input. Switching off the key reverses this process.

## Button
Hitting the button does nothing without the key being turned. When the key is primed, button presses can be detected and that signals to BassGPT to fire the user request and begin the API flow.
# Frankenfish
Bring BassGPT to life. 

Frankenfish is an Arduino project which communicates with the main BassGPT Java application to sync mouth and torso movements with audio.

## API Endpoints

### GET `http://localhost:80/`
Check status of webserver.

### POST `http://localhost:80/query`
Send a buffer to the fish to immediately be animated. Each bit represents the mouth state at 100ms intervals (1: agape, 2: closed).

Example request:
```json
{
  "buffer": [0,0,0,0,0,0,0,0,0,0,1,1,1,1,1,1,1,1,1,1,0,1,0,1,0,1,0,1,0,1]
}
```
The above request represents a mouth animation of: 1 second open, 1 second closed, and then rapidly opening and closing.

### POST `http://localhost:80/torso`
Send a torso activation state to immediately be animated.

Example request:
```json
{
  "value": true
}
```
The above request will immediately bend the fish torso.
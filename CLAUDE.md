This project is about an android app that bridges USB obd2 devices to TCP so apps that allow "wifi" obd2 devices can communicate with USB obd2 devices.

The resulting program is intended to run in an android head unit in a car.

The main considerations:
- The app should not crash
- If there is something unexpected or an error, it should recover gracefully
- The car android OS will call the Service in this app, there can be multiple calls and the service must be running without being altered.
- Be running always in background
- Consume as little resources as possible
# android-box-game

Make more squares than your opponent to win.

## Multiplayer Firebase setup

This app supports local play and simple 2-player online rooms using Firebase Anonymous Auth plus Realtime Database.

1. Create or open a Firebase project.
2. Add an Android app with package name `com.example.boxgame`.
3. Download `google-services.json` from Firebase and place it at:

   ```text
   app/google-services.json
   ```

4. In Firebase Authentication, enable the `Anonymous` sign-in provider.
5. Create a Realtime Database instance. Start locked down, then deploy the rules in `database.rules.json`.
   If multiplayer sign-in shows `CONFIGURATION_NOT_FOUND`, Firebase Authentication or the Anonymous provider is not enabled for the Firebase project used by `app/google-services.json`.
6. If you use the Firebase CLI, deploy the included rules with:

   ```shell
   firebase deploy --only database
   ```

Firebase docs:

- Android setup: https://firebase.google.com/docs/android/setup
- Anonymous Auth: https://firebase.google.com/docs/auth/android/anonymous-auth
- Realtime Database presence / `onDisconnect()`: https://firebase.google.com/docs/database/android/offline-capabilities
- Realtime Database rules: https://firebase.google.com/docs/database/security

## Testing multiplayer

1. Install the app on two devices or two emulators.
2. On device 1, enter Player 1 initials, choose a board size, and tap `Create Game`.
3. Share the displayed 6-character room code.
4. On device 2, enter Player 2 initials, enter the room code, and tap `Join Game`.
5. Moves should appear live on both screens. Only the current player can submit a move.
6. To test presence, close the app or disable networking on one device. The other device should show an opponent disconnected/left state.

`google-services.json` is intentionally ignored by git. Keep your real Firebase config local or provide it through your normal secret/config workflow.

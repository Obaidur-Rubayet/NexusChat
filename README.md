# Nexus Chat

<p align="center">
  <img src="assets/nexus-chat-logo.png" alt="Nexus Chat Logo" width="140" />
</p>

<h3 align="center">Connect • Chat • Belong</h3>

<p align="center">
  A modern Android messaging application built with Kotlin and Jetpack Compose.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white" alt="Android" />
  <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
  <img src="https://img.shields.io/badge/Backend-Firebase-FFCA28?logo=firebase&logoColor=black" alt="Firebase" />
</p>

## About Nexus Chat

**Nexus Chat** is an Android messaging application project designed to make communication simple, fast, and convenient. It aims to provide a clean user interface, real-time messaging, user profiles, and privacy-focused communication.

The project is built using modern Android development technologies and is designed for future expansion.

## Features

- User authentication and profile management
- One-to-one and group conversations
- Real-time messaging with Firebase
- Online presence and last-seen status
- Typing indicators
- Image and file attachments
- Modern interface built with Jetpack Compose
- Dark and light themes
- Security-focused database rules
- End-to-end encryption — planned or under development until fully implemented and tested

*Feature availability depends on the current implementation.*

## Tech Stack

| Technology | Purpose |
|---|---|
| Kotlin | Android application development |
| Jetpack Compose | Modern UI development |
| Material 3 | UI components and theming |
| Firebase Authentication | User authentication |
| Cloud Firestore | Chat and user data |
| Firebase Storage | Media and file storage, if configured |
| Gradle | Build automation |

## Project Structure

```text
NexusChat/
├── app/
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
├── firebase.json
├── firestore.rules
├── firestore.test.js
├── firebase-blueprint.json
├── metadata.json
├── .env.example
├── .gitignore
└── README.md
```

## Getting Started

### Prerequisites

- Android Studio
- JDK compatible with the project's Gradle configuration
- Android SDK
- A Firebase project for Firebase-backed features

### Installation

1. Clone the repository:

   ```bash
   git clone https://github.com/Obaidur-Rubayet/NexusChat.git
   ```

2. Open the project in Android Studio:

   ```text
   NexusChat/
   ```

3. Wait for Gradle Sync to finish.

4. Configure Firebase if required. Use your own Firebase project and keep private credentials out of Git.

5. Run the application on an Android emulator or compatible device.

## Logo

The README logo is loaded from:

```text
assets/nexus-chat-logo.png
```

Add your logo image at that location to display it at the top of this README.

## Security

Security is an important part of Nexus Chat. The project includes Firestore rules and a data model for messaging-related features.

- Do not commit API secrets, private keys, passwords, or signing credentials.
- Restrict Firestore access according to authentication and conversation membership.
- Validate security rules before deploying them.
- Do not describe the application as end-to-end encrypted until the encryption protocol, key management, and message integrity protections have been implemented and independently tested.

## Development Status

Nexus Chat is under development. Features, security controls, and Firebase integration should be tested before production use.

## Contributing

Contributions, suggestions, and bug reports are welcome. Please open an issue to discuss significant changes before submitting a pull request.

## License

No license has been specified yet. Until a license is added, all rights remain with the copyright holder.

---

<p align="center">
  <strong>Nexus Chat</strong><br/>
  Connect • Chat • Belong
</p>

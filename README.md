<h1 align="center"> BLE Mesh Messaging App</h1>

<p align="center">
  Decentralized Offline Communication System Using Bluetooth Low Energy (BLE)
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-green">
  <img src="https://img.shields.io/badge/Language-Kotlin-purple">
  <img src="https://img.shields.io/badge/Communication-BLE-blue">
</p>

---

#  Overview

This project is an Android-based decentralized communication system built using Bluetooth Low Energy (BLE) mesh networking.

The application enables devices to communicate without requiring:
- Internet
- Wi-Fi
- Cellular networks

Messages are relayed across nearby devices using mesh communication principles, allowing offline peer-to-peer messaging.

---

# Features

-  Offline BLE communication
-  Mesh-based message forwarding
-  Android native application
-  Low-power BLE connectivity
-  Multi-hop message relay
-  Decentralized architecture
-  Proximity-based communication possibilities
-  Emergency communication support

---

#  System Architecture

```text
Device A ↔ Device B ↔ Device C ↔ Device D
```

Messages are forwarded through nearby devices until they reach the target device.

---

#  Tech Stack

| Technology | Usage |
|---|---|
| Kotlin | Android Development |
| Android Studio | Development Environment |
| BLE | Wireless Communication |
| GATT | BLE Data Exchange |
| Mesh Networking | Message Relaying |

---

#  Installation

## 1. Clone the Repository

```bash
git clone https://github.com/giribeans29/ble-msg-app-.git
```

## 2. Open in Android Studio

Open the project folder using Android Studio.

## 3. Sync Dependencies

Allow Gradle to sync all dependencies.

## 4. Run on Physical Devices

> BLE features may not function correctly on emulators.

---

#  Permissions Required

```xml
<uses-permission android:name="android.permission.BLUETOOTH"/>
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN"/>
<uses-permission android:name="android.permission.BLUETOOTH_SCAN"/>
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT"/>
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
```

#  Project Goals

This project aims to explore:
- Real-time wireless communication
- Embedded Android systems
- Decentralized networking
- BLE mesh architectures
- Emergency communication systems

---

#  Future Improvements

- End-to-end encryption
- Dynamic routing algorithms
- Low-power optimization
- Secure device authentication
- Message persistence
- Smart relay selection
- Sensor-assisted communication logic

---

#  Contributor

- **Girish**

---

#  License

This project is licensed under the MIT License.

---

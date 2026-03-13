<div align="center">

# 🛡️ SafePulse

### Your Personal Safety Companion

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-purple.svg)](https://kotlinlang.org/)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://android-arsenal.com/api?level=26)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

*Stay safe with real-time monitoring, emergency alerts, and intelligent safety features*

[Features](#-features) • [Installation](#-installation) • [Usage](#-usage) • [Tech Stack](#-tech-stack) • [Contributing](#-contributing)

</div>

---

## 📱 About SafePulse

SafePulse is an advanced personal safety application designed to keep you protected with intelligent monitoring, emergency response features, and data-driven safety insights. Whether you're walking alone at night, traveling to unfamiliar areas, or want to ensure your loved ones' safety, SafePulse has you covered.

### 🎯 Key Highlights

- 🚨 **Instant SOS Alert** - Quick emergency response at the touch of a button
- 📍 **Live Location Tracking** - Real-time GPS monitoring with background support
- 🗣️ **Voice-Activated Emergency** - Hands-free emergency activation
- 📷 **Auto Photo Capture** - Discrete documentation during emergencies
- 🗺️ **Crime & Safety Maps** - Data-driven insights on area safety
- 💾 **Offline Mode** - Core safety features work without internet
- 🔔 **Smart Notifications** - Context-aware alerts and reminders

---

## ✨ Features

### 🆘 Emergency Response
- **One-Touch SOS**: Instantly alert emergency contacts with your location
- **Auto SMS/Call**: Automatically send distress messages and initiate calls
- **SOS Cancellation**: Built-in countdown to prevent false alarms
- **Emergency Photo**: Captures front camera photo when SOS is triggered

### 📍 Location & Tracking
- **Real-Time GPS**: Precise location tracking with Google Maps integration
- **Background Monitoring**: Continuous tracking even when app is closed
- **Location Sharing**: Share your live location with trusted contacts
- **Journey Tracking**: Monitor your route and receive alerts for unsafe areas

### 🤖 Intelligent Safety Features
- **Voice Trigger**: Activate emergency mode with voice commands
- **Activity Recognition**: Detect sudden movements or falls
- **Safe Zone Alerts**: Get notified when entering/leaving designated areas
- **Auto Boot Service**: Restarts protection after device reboot

### 📊 Data & Analytics
- **Crime Data Analysis**: Access comprehensive India crime statistics
- **Landslide Risk Zones**: Geographical hazard information
- **Safety Score**: AI-powered area safety ratings
- **Historical Insights**: Learn from past incident data

### 🔐 Privacy & Security
- **Local Data Storage**: Your information stays on your device
- **Encrypted Communications**: Secure message transmission
- **Permission Control**: Granular control over app permissions
- **No Tracking**: We don't monitor you unless you activate safety features

---

## 🚀 Installation

### Prerequisites

- **Android Device**: API Level 26 (Android 8.0 Oreo) or higher
- **Google Play Services**: Required for Maps integration
- **Permissions**: Location, SMS, Phone, Camera, Microphone

### Setup Steps

1. **Clone the Repository**
   ```bash
   git clone https://github.com/Elson1603/SafePulse.git
   cd SafePulse
   ```

2. **Configure Google Maps API**
   
   Create a `local.properties` file in the root directory:
   ```properties
   MAPS_API_KEY=your_google_maps_api_key_here
   ```
   
   > 🔑 Get your API key from [Google Cloud Console](https://console.cloud.google.com/)

3. **Build the Project**
   ```bash
   ./gradlew build
   ```

4. **Install on Device**
   ```bash
   ./gradlew installDebug
   ```

### Download APK

> 📦 Pre-built APKs will be available in the [Releases](https://github.com/Elson1603/SafePulse/releases) section

---

## 🎮 Usage

### First Time Setup

1. **Grant Permissions**: Allow all requested permissions for full functionality
2. **Add Emergency Contacts**: Set up your trusted contacts who will receive SOS alerts
3. **Configure Settings**: Customize alert preferences and notification settings
4. **Test SOS**: Do a test run to ensure everything works correctly

### Daily Use

#### Activate Protection
Simply open the app and enable the background service. SafePulse will:
- ✅ Monitor your location in the background
- ✅ Listen for emergency voice commands
- ✅ Track unusual activity patterns
- ✅ Stay ready to send instant alerts

#### Trigger Emergency Alert

**Method 1: Manual SOS**
- Tap the SOS button on the home screen
- Confirm or wait for auto-trigger countdown
- Emergency contacts receive your location and alert message

**Method 2: Voice Activation**
- Say the configured emergency phrase
- Instant alert without unlocking phone

**Method 3: Shake Detection**
- Vigorous shaking triggers emergency mode
- Useful when unable to access phone

### Exploring Safety Data

Navigate to the **Safety Insights** section to:
- 🗺️ View crime hotspots on interactive maps
- 📈 Analyze safety trends in your area
- ⚠️ Check for natural disaster risk zones
- 🎯 Get safety recommendations

---

## 🛠️ Tech Stack

### Core Technologies

| Component | Technology |
|-----------|-----------|
| **Language** | Kotlin |
| **UI Framework** | Jetpack Compose + Material3 |
| **Architecture** | MVVM (Model-View-ViewModel) |
| **Dependency Injection** | Manual/Factory Pattern |
| **Async Operations** | Kotlin Coroutines + Flow |

### Key Libraries

```gradle
🎨 UI & Design
├── Jetpack Compose (Modern UI toolkit)
├── Material Design 3 (UI components)
├── Compose Navigation (Screen navigation)
└── Material Icons Extended (Icon library)

💾 Data & Storage
├── Room Database (Local data persistence)
├── DataStore (Preferences storage)
└── KSP (Annotation processing)

📍 Location & Maps
├── Google Maps SDK (Map visualization)
├── Location Services (GPS tracking)
└── Geofencing API (Zone monitoring)

⚙️ Background Processing
├── WorkManager (Scheduled tasks)
├── Foreground Service (Continuous monitoring)
└── BroadcastReceiver (System events)

🔧 Utilities
├── Lifecycle Components (Android lifecycle)
├── ViewModel (State management)
└── LiveData/StateFlow (Reactive data)
```

---

## 📂 Project Structure

```
SafePulse/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/safepulse/
│   │   │   │   ├── MainActivity.kt          # Entry point
│   │   │   │   ├── SafePulseApplication.kt  # Application class
│   │   │   │   ├── service/                 # Background services
│   │   │   │   │   ├── SafetyForegroundService.kt
│   │   │   │   │   ├── BootReceiver.kt
│   │   │   │   │   └── SOSCancelReceiver.kt
│   │   │   │   ├── ui/                      # Compose UI screens
│   │   │   │   ├── data/                    # Data models & repositories
│   │   │   │   ├── viewmodel/               # ViewModels
│   │   │   │   └── utils/                   # Helper functions
│   │   │   ├── res/                         # Resources
│   │   │   └── AndroidManifest.xml
│   │   └── test/                            # Unit tests
│   └── build.gradle.kts
├── crime_dataset_india.csv                  # Crime statistics data
├── landslide.csv                            # Landslide risk data
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

---

## 🔐 Permissions Explained

SafePulse requires several permissions to function effectively:

| Permission | Purpose | Required? |
|-----------|---------|-----------|
| 📍 **ACCESS_FINE_LOCATION** | Precise GPS tracking for emergencies | ✅ Essential |
| 📍 **ACCESS_BACKGROUND_LOCATION** | Continuous monitoring when app closed | ✅ Essential |
| 💬 **SEND_SMS** | Send emergency alerts to contacts | ✅ Essential |
| 📞 **CALL_PHONE** | Initiate emergency calls | ✅ Essential |
| 🔔 **POST_NOTIFICATIONS** | Display alerts and updates | ⚠️ Important |
| 🎤 **RECORD_AUDIO** | Voice-activated emergency trigger | 📱 Optional |
| 📷 **CAMERA** | Capture photos during SOS | 📱 Optional |
| 🏃 **ACTIVITY_RECOGNITION** | Detect falls or unusual movement | 📱 Optional |

---

## 🤝 Contributing

We welcome contributions from the community! Whether it's bug fixes, new features, or documentation improvements, your help is appreciated.

### How to Contribute

1. **Fork the Repository**
   ```bash
   git clone https://github.com/YOUR-USERNAME/SafePulse.git
   ```

2. **Create a Feature Branch**
   ```bash
   git checkout -b feature/AmazingFeature
   ```

3. **Make Your Changes**
   - Write clean, documented code
   - Follow Kotlin coding conventions
   - Add tests for new features

4. **Commit Your Changes**
   ```bash
   git commit -m "Add: Amazing new safety feature"
   ```

5. **Push to Your Fork**
   ```bash
   git push origin feature/AmazingFeature
   ```

6. **Open a Pull Request**
   - Describe your changes clearly
   - Reference any related issues
   - Wait for review and approval

### Development Guidelines

- 📝 **Code Style**: Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- ✅ **Testing**: Write unit tests for new functionality
- 📚 **Documentation**: Update README and code comments
- 🐛 **Bug Reports**: Use GitHub Issues with detailed reproduction steps
- 💡 **Feature Requests**: Open an issue to discuss before implementing

---

## 🗺️ Roadmap

### Version 1.1 (Coming Soon)
- [ ] iOS version development
- [ ] Integration with wearable devices
- [ ] Multi-language support
- [ ] Enhanced AI-based threat detection

### Version 1.2
- [ ] Family safety circle features
- [ ] Integration with local emergency services
- [ ] Offline maps support
- [ ] Custom emergency protocols

### Future Ideas
- [ ] Community safety reporting
- [ ] Integration with smart home devices
- [ ] Health monitoring integration
- [ ] Public transport safety features

---

## 📊 Data Sources

SafePulse uses publicly available datasets to provide safety insights:

- **Crime Data**: Comprehensive India crime statistics (`crime_dataset_india.csv`)
- **Landslide Data**: Geographical hazard information (`landslide.csv`)
- **Maps**: Google Maps Platform for visualization

*All data is used for informational purposes only and should not be the sole basis for safety decisions.*

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

```
MIT License

Copyright (c) 2026 SafePulse Team

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:
[...full license text...]
```

---

## 🙏 Acknowledgments

- **Google Maps Platform** for mapping services
- **Android Open Source Project** for the robust mobile framework
- **Jetpack Compose Team** for the modern UI toolkit
- **Open Data Sources** for crime and safety statistics
- **Community Contributors** for their valuable input

---

## 📞 Support & Contact

### Need Help?

- 📧 **Email**: support@safepulse.app *(placeholder)*
- 🐛 **Bug Reports**: [GitHub Issues](https://github.com/Elson1603/SafePulse/issues)
- 💬 **Discussions**: [GitHub Discussions](https://github.com/Elson1603/SafePulse/discussions)
- 📖 **Documentation**: [Wiki](https://github.com/Elson1603/SafePulse/wiki)

### Emergency Resources

> ⚠️ **Important**: SafePulse is a personal safety tool, not a replacement for professional emergency services.

- 🇮🇳 India Emergency: **112** (National Emergency Number)
- 👮 Police: **100**
- 🚑 Ambulance: **102**
- 🚒 Fire: **101**
- 👩 Women Helpline: **1091**

---

## 🌟 Star History

If you find SafePulse helpful, please consider giving it a star! ⭐

[![Star History Chart](https://api.star-history.com/svg?repos=Elson1603/SafePulse&type=Date)](https://star-history.com/#Elson1603/SafePulse&Date)

---

<div align="center">

### Made with ❤️ for Your Safety

**SafePulse** - Because everyone deserves to feel safe

[⬆ Back to Top](#-safepulse)

</div>

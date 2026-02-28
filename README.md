SAFE JOURNEY - Travel Safety Android Application

Project Description

Safe Journey is an Android-based travel safety application designed to
enhance personal security during trips. The app provides real-time GPS
tracking, estimated time of arrival (ETA) calculation, route monitoring,
and automatic SOS alerts. Users can enter a destination, select a mode
of transport (Car, Bike, Taxi, etc.), and start their journey with a
safety countdown timer.

If the user does not manually stop the journey before the estimated
arrival time, the application automatically triggers an emergency SOS
message. In Taxi mode, the app monitors route deviation and alerts the
user if they move away from the expected path. The application also
calculates destination risk using demo data such as crime rate, street
light count, and residential density.

Tech Stack

-   Kotlin (Android Development)
-   Android Studio
-   OSMDroid (OpenStreetMap integration)
-   Fused Location Provider (GPS tracking)
-   SMS Manager (Emergency alerts)
-   ConstraintLayout (UI design)

Key Features

-   Live GPS tracking with map view
-   ETA calculation based on transport mode
-   Countdown-based journey monitoring
-   Automatic SOS message trigger
-   Route deviation detection (Taxi mode)
-   Destination risk calculation using demo datasets
-   Emergency SMS with location link

Build Instructions

1.  Clone the repository: git clone

2.  Open the project in Android Studio.

3.  Sync Gradle files.

4.  Connect an emulator or Android device.

5.  Click Run â–¶ to build and install the APK.

Installation Guide

1.  Enable â€œInstall from Unknown Sourcesâ€ in Android settings.
2.  Install the generated APK file.
3.  Grant Location and SMS permissions when prompted.
4.  Enter destination and start journey.

App Flow

User enters destination â†’ Selects transport mode â†’ ETA calculated â†’
Countdown starts â†’ Route monitored â†’ If stopped manually â†’ Journey ends
â†’ If not stopped or deviation detected â†’ SOS triggered.

Architecture

MainActivity | |â€“ Location Service (GPS tracking) |â€“ Risk Calculation
Module |â€“ Route Monitoring Module |â€“ SOS & SMS Module |â€“ UI Layer (Map +
Controls)

Team Members

1.  Rifa mariyam
2.  pavithra k

GitHub Repository

Repository Link: https://github.com/rifaamariyam/safejourney.git

License

This project is developed for academic purposes. Open-source usage
permitted for learning and educational use.

# 🧾 Digital Business Card – Android (Kotlin)

A modern Android app to create, store, sync, share, and manage beautiful digital business cards. Build cards with rich profiles, generate QR codes, export to PDF (with clickable actions), and keep everything synced with the cloud.
## 📲 Get the App

Download the latest pre-compiled application package:
👉 **[Download Digital Business Card APK](Businesscard%20application.apk)**
---

## ✨ Highlights

- 🖊️ Create Custom Cards
  Name, title, phone, email, address, website, LinkedIn, Instagram, photo/logo.

- 🎨 New Modern UI
  Clean Material Design 3 with card-style list and detail screens.

- 🔐 Authentication
  Sign in with Google (One Tap) or Gmail-based auth.

- ☁️ Cloud Sync + 🔄 Offline-first
  Save locally and sync to Firebase. Works offline; syncs when online.

- � Backup & Restore
  Export your saved cards to a backup file and restore anytime (useful for migration and safety).

- �📤 Export as PDF (Interactive)
  PDF with clickable actions: call, maps, email, Instagram, website.

- 📷 QR Codes
  Generate and scan QR to share/save cards seamlessly.

- 💬 Push Notifications
  Firebase Cloud Messaging for updates and reminders.

- 🗂️ Multi-Card Support
  Create, save, search, and manage multiple cards.

- 🎨 Themes & Customization Options
  Choose from beautiful pre-defined color themes or tailor the card with options like text size, font weight, layout density, pill corner radius, borders, and element visibility.

- 🧹 Account Management
  Delete account flow with cleanup (local + cloud, based on sync settings).

- 🔒 Privacy-first
  All data available locally; cloud sync is opt-in.

---

## 🛠 Tech Stack (Overview)

- Language: Kotlin
- UI/UX: Material Design 3, Jetpack Components
- Local Storage: Room / Jetpack DataStore
- Cloud: Firebase (Auth, Firestore or Realtime DB, Cloud Messaging, Storage as needed)
- QR: ZXing
- PDF (No iText): Android Print Framework via WebView (HTML → PDF)
- Images: Glide / Picasso

---

## 🧾 PDF Generation Notes (HTML → PDF)

- Render the card as HTML in a WebView.
- Use createPrintDocumentAdapter + PrintManager to generate a PDF.
- Embed clickable links in HTML (tel:, mailto:, https://, geo:) so they are interactive in the PDF.

---

## 🧭 Core Workflows

- Create Card:
  Add details → live preview → save (local + optional cloud sync).

- Share Card:
  Generate QR → recipient scans to import.
  Export PDF → share via any app.
  Share as image (PNG/JPEG).

- PDF Export (Interactive):
  Phone → Dialer.
  Email → Mail app.
  Address → Maps.
  Website/Instagram → Browser/app.

- Sync:
  Enable cloud sync in settings → keep cards synced across devices.

- Backup / Restore:
  Backup cards → share/save the backup file.
  Restore cards → import from a backup file.

- Themes / Customize:
  Open the navigation drawer and select "Theme" to apply color palettes, or "Customize" to adjust visual details such as density, radius, border, font sizes, and toggles.

- Delete Account:
  Remove account and associated data (with confirmation).

- Notifications:
  Receive reminders or updates via FCM.

---

## 🎨 Themes & Customization Options

The application supports real-time styling of digital cards. Visual preferences are saved locally in the app's preferences (`SharedPreferences`), offering quick and dynamic styling modifications.

### 🌈 Color Themes

Choose from four distinct color templates tailored to provide a professional layout:
- **🔴 Classic Red**: Crimson (`#A82120`) headers and accent action buttons over a clean, neutral background.
- **🔵 Ocean Blue**: Cobalt (`#1565C0`) headers and accents with an ocean-themed style.
- **🟢 Forest Green**: Earthy Green (`#2E7D32`) elements for a fresh, organic business presence.
- **⚫ Midnight**: Charcoal/Dark Slate (`#263238`) details for a sleek, high-contrast, modern aesthetic.

*Each theme dynamically controls the page background, card header, details container fill, text color, bottom navigation bar, action icon background, and icon tint.*

### 🛠️ Visual Customization Settings

Customize the layout, structure, and text details of the digital card through the **Customize Card** interface:
1. **Typography & Hierarchy**:
   - **Text Size**: Adjust standard detail text sizing to **Small (14sp)**, **Normal (16sp)**, or **Large (18sp)**. The card's title/name auto-scales (+8sp larger) and occupation (+2sp) to preserve hierarchical balance.
   - **Font Style**: Toggle **Bold text** across all text fields.
2. **Card Spacing & Layout**:
   - **Compact Spacing**: Toggle between standard/relaxed spacing and compact layout to fit more content or create a snug UI.
3. **Element Visibility**:
   - **Contact Icons**: Show or hide helper icons (Phone, Address, Email, Website, Instagram) inside the card pills.
   - **Website / Instagram Row**: Toggle individual detail row visibility to omit optional links seamlessly.
   - **Action Labels**: Show or hide text labels beneath the action icons (QR, Scan QR, Saved Cards, Share Image) in the bottom navigation bar.
4. **Pill Shape & Styling**:
   - **Pill Corner Radius**: Choose between **Small (12dp)**, **Medium (24dp)**, or **Large (32dp)** corners for detail pill containers.
   - **Pill Border**: Enable or disable a fine border outline around each detail container.

---

## 🧩 App Structure (example)

- ui/ — screens, components, theming
- data/local/ — Room entities, DAO
- data/remote/ — Firebase services (Auth, Firestore/DB, Storage)
- domain/ — models, use-cases
- utils/ — QR, PDF, share helpers

---

## 🔐 Permissions

- CAMERA — QR scanning
- POST_NOTIFICATIONS — Android 13+
- Storage/Media — Use scoped storage or MediaStore as appropriate for exports

---

## 📡 Firebase Notes

- Auth: Google Sign-In / One Tap
- Database: Firestore or Realtime Database (choose one consistently)
- Storage: Optional for avatars/logos
- Messaging: Handle token refresh and notification channels (Android 8+)

---

## 🔍 QR & PDF

- QR: ZXing for generation and scanning.
- PDF:
  - Android PdfDocument via WebView Print Framework for vector output.
  - Use URL annotations through HTML links for interactivity in the exported PDF.

---

## ✅ Quality

- Kotlin Coroutines & Flows
- ViewModel state handling
- Material 3 theming & accessibility
- Offline-first with conflict handling on sync

---

## 🧪 Testing

- Unit tests for QR data and PDF link generation
- Instrumented tests for DB and navigation

---

## 🛡️ Privacy

- Local-first storage
- Cloud sync is opt-in and tied to the user’s account
- No data sold to third parties

---

## 🗺️ Roadmap

- [x] Themes + customization
- [ ] Card templates
- [ ] Drag-and-drop layout builder
- [ ] Privacy-friendly analytics for shares/opens
- [ ] Multi-language support

---

## 🤝 Contributing

- Closed development. External contributions are not accepted at this time.

---

## 🔒 License (Proprietary – No Redistribution)

This software is proprietary and confidential. All rights reserved.

By using this application, you agree to the following restrictions:
- ❌ No copying, distributing, sublicensing, or publishing the source code or binaries.
- ❌ No modification, reverse engineering, decompiling, or creating derivative works.
- ❌ No commercial use, resale, or hosting as a service without prior written permission from the owner.

---

## ©️ Copyright
Copyright © 2025 Pankaj Raut. All rights reserved.


## 🖼️ Screenshots

Place images under: docs/screenshots/
- Home list (new UI)
- Create/Edit card
- QR generate/scan
- PDF preview
- Sign-in with Google

<img width="244" height="520" alt="image" src="https://github.com/user-attachments/assets/0885aabd-0cfe-41e6-9144-8d48ae1b431a" />
<img width="244" height="520" alt="image" src="https://github.com/user-attachments/assets/e70cb483-1918-470b-ac6f-88c7f0771d15" />
<img width="244" height="520" alt="image" src="https://github.com/user-attachments/assets/5cc613ca-e88d-4890-8869-2cc6cf7c763d" />
<img width="244" height="520" alt="image" src="https://github.com/user-attachments/assets/df28807e-029d-48e4-b1e0-86a16729fc28" />
<img width="244" height="520" alt="image" src="https://github.com/user-attachments/assets/6457626b-5cbc-40a3-a6fe-b19891b15ee1" />
<img width="244" height="476" alt="image" src="https://github.com/user-attachments/assets/e027500f-bb23-4a3d-9ac0-adb04208a292" />
<img width="244" height="520" alt="image" src="https://github.com/user-attachments/assets/423fb6e6-4c2e-453d-8690-e3641877597a" />
<img width="244" height="468" alt="image" src="https://github.com/user-attachments/assets/19d15a9a-b3f0-436e-8cfe-a2a6476a2f6d" />

---

## 📬 Contact

Name: Pankaj Raut
Email: r9pankaj@gmail.com


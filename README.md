# 👑 Rustle — Timeless PDF Book Reader & Annotator

An advanced, feature-rich, and highly customizable Android PDF Reader built entirely with **Jetpack Compose**. Designed for readers, students, and professionals who crave an immersive reading experience, **Rustle** blends elegant dark-mode aesthetics with powerful productivity tools like AI-powered OCR text scanning, interactive drawing tools, custom page textures, and ambient background audio.

<div align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" />
  <img src="https://img.shields.io/badge/Built%20With-Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" />
  <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" />
  <img src="https://img.shields.io/badge/AI%20Engine-Google%20ML%20Kit-FF6F00?style=for-the-badge&logo=google&logoColor=white" />
</div>

---

## ✨ Why Rustle is Different

Unlike standard PDF viewers that feel static and rigid, Rustle turns digital reading into an interactive, tactile, and soothing experience. Whether you want to scribble margin notes on an "Old Paper" texture while listening to ambient rain, or instantly circle and translate foreign text using built-in OCR, Rustle adapts to your workflow.

---

## 🚀 Key Features

### 📖 Immersive Reading & Personalization
* **Custom Page Textures:** Choose from curated aesthetic themes (*Old Paper, Cream, Manuscript, Parchment, Clean White, Sepia Warm, Zen Mint, Nordic Blue*) or **upload custom background images directly from your device gallery**.
* **Ambient Rain Audio (🌧️):** Stay focused with built-in, looping background rain sounds featuring a real-time volume slider.
* **Realistic Audio Feedback:** Enjoy tactile page-flip sound effects as you navigate through your books.
* **Text-to-Speech (TTS) Read Aloud (🔊):** Let the app read to you! Rustle automatically extracts text from the current page and reads it aloud with responsive progress banners.

---

### 🧰 The Interactive Draggable Toolbox
Access a floating, draggable toolbox (`🧰`) that can be repositioned anywhere on the screen for effortless annotation:
* **🖊️ Pen Tool:** Freehand drawing and margin scribbling with smooth stroke rendering.
* **🖍️ Highlighter:** Semi-transparent, wide-stroke highlighting for important quotes.
* **🧽 Precision Eraser:** Touch-based stroke deletion that removes annotations without damaging the underlying PDF.
* **📝 Draggable Sticky Notes:** Tap anywhere to drop editable, bright yellow sticky notes. Drag them across the screen, edit their text, or delete them on the fly.
* **🔖 Instant Bookmarking:** Tag key pages with a single tap for quick retrieval later.

---

### ⭕ AI-Powered OCR Text Scanner
* **Circle-to-Scan:** Select the Scanner tool (`⭕`) and draw a circle around any paragraph, diagram, or quote on the PDF page.
* **Instant Text Extraction:** Powered by **Google ML Kit Vision**, the app instantly extracts text contained within your drawn boundary.
* **📋 Copy & Highlight:** Manually highlight specific phrases or tap the **Copy** button to push the entire extracted text directly to your Android clipboard with a Toast notification.
* **🌐 One-Tap Translation:** Instantly send scanned text to Google Translate with auto-language detection.

---

### 🧭 Advanced Navigation & Search
* **🔍 Full-Book Keyword Search:** Type any word or phrase to scan the entire document. Features a real-time progress bar, snippet previews, and a **"Teleport ➡️"** button to jump directly to matching pages.
* **🔲 Page Overview Grid:** Open a responsive visual thumbnail grid of every page in the book to visually jump to new chapters.
* **📑 Bookmarks Teleport Dashboard:** A dedicated hub displaying all your bookmarked pages with preview thumbnails for instant teleportation.
* **⚡ Responsive Scrubber Slider:** Glide through hundreds of pages smoothly with the bottom navigation slider.

---

### 📱 Responsive & Adaptive Design
* **Tablet & Foldable Ready:** Automatically detects device screen size (`smallestScreenWidthDp >= 600`) and dynamically scales grid columns, dialog widths, toolbar icons, and font sizes for tablets and large displays.
* **Full-Screen Video Splash:** Features a native, stretched video splash screen with tap-to-skip functionality and a custom Rustle Gold branding badge.

---

### 💾 Smart Data Persistence
* **Unique Book Tracking:** Annotations, drawings, sticky notes, and bookmarks are automatically serialized and saved locally per unique book (`*.dat`), ensuring your notes never bleed into other files or get lost when you close the app.
* **Sample Guardrail:** Built-in protection that ignores temporary or default sample files from overwriting your personal library data.

---

## 🛠️ Tech Stack & Architecture

* **UI Framework:** [Jetpack Compose](https://developer.android.com/jetpack/compose) (Material 3 Design System)
* **Language:** Kotlin (Coroutines & Asynchronous Flows)
* **Machine Learning / OCR:** [Google ML Kit Text Recognition](https://developers.google.com/ml-kit/vision/text-recognition)
* **Media & Audio:** Native Android `MediaPlayer`, `TextToSpeech` Engine, and custom `VideoView` interop
* **State Management:** `ViewModel` with lifecycle-aware Compose state (`remember`, `mutableStateMapOf`, `mutableStateListOf`)
* **Persistence:** Custom Java Serialization & Android File System I/O

---

## ⚙️ Installation & Setup

1. **Clone the Repository:**
   ```bash
   git clone [https://github.com/SanjeevKumar-24/BOOKL1.git](https://github.com/SanjeevKumar-24/BOOKL1.git)
   cd BOOKL1

   
👤 Author & Connect
Sanjeev Kumar (SJV)

Passionate Android Developer & Independent Author

GitHub: @SanjeevKumar-24

LinkedIn: Sanjeev Kumar

Instagram: @sanjeeveram

Feedback & Support: Sanjeevkumarmaner90@gmail.com
   

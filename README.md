# Mobile Object Detector

**Mobile Object Detector** is an educational Android application for real-time object detection on mobile devices.

The app uses a YOLOv8 LiteRT/TFLite model from Hugging Face and runs live camera-based object detection with CameraX. It includes model downloading, live camera preview, bounding-box visualization, confidence threshold adjustment, IoU/NMS settings, and class filtering.

> Written by **Dr. Vahid Tavakkoli**, 2026, for educational purposes.

---

## Features

- Android Java application
- Real-time camera preview using CameraX
- YOLOv8 object detection using LiteRT/TFLite
- Button to download the model from Hugging Face
- Live object detection activity
- Bounding boxes, labels, and confidence values
- Adjustable confidence threshold
- Adjustable NMS IoU threshold
- Selectable COCO classes for detection
- Simple educational project structure

---

## Model

The app is prepared for the Hugging Face model:

```text
SpotLab/YOLOv8Detection
```

Expected model file:

```text
yolov8n_saved_model/yolov8n_float16.tflite
```

The model is downloaded by the app and stored in the app's private storage.

Model repository:

```text
https://huggingface.co/SpotLab/YOLOv8Detection
```

Important: the external model is not owned by this project. Check the Hugging Face model card and license before using it outside educational or research work.

---

## Technology Stack

- Java
- Android Studio
- CameraX
- TensorFlow Lite / LiteRT-compatible `.tflite` inference
- YOLOv8 post-processing
- COCO class labels
- Gradle Kotlin DSL

---

## Project Information

```text
App name:       Mobile Object Detector
Project name:   MobileObjectDetector
Package name:   ai.mobileobjectdetector
Minimum SDK:    API 27 / Android 8.1
Language:       Java
```

---

## How to Run

1. Clone or download this repository.
2. Open the project in Android Studio.
3. Let Gradle sync the project.
4. Connect an Android device or start an emulator with camera support.
5. Build and run the app.
6. Press **Load Model from Hugging Face**.
7. Grant camera permission.
8. Press **Open Live Camera Detection**.

---

## Main Screens

### Main Activity

The main screen provides:

- model download button
- live camera detection button
- settings shortcut
- confidence threshold control
- selected class summary
- project author information

### Camera Detection Activity

The camera screen provides:

- live camera preview
- YOLOv8 inference
- detection bounding boxes
- class labels
- confidence scores

### Settings Activity

The settings screen provides:

- confidence threshold
- NMS IoU threshold
- class filtering

---

## Educational Purpose

This project is created for teaching and learning mobile AI, computer vision, and on-device inference. It is not intended for safety-critical, medical, legal, or production-grade use without further testing, validation, and optimization.

---

## Author

**Dr. Vahid Tavakkoli**  
2026

---

## License

This project is released under the MIT License.

See the [LICENSE](LICENSE) file for details.

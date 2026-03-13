"""
SafePulse Voice Trigger - Keyword Spotting Model Trainer
=========================================================
Trains a TFLite keyword spotting model from Audio_Dataset3.

Usage:
    cd Audio_Dataset3
    pip install tensorflow librosa numpy scikit-learn
    python train_model.py

Output:
    keyword_spotting_model.tflite  -> copy to app/src/main/assets/
    keyword_labels.txt             -> copy to app/src/main/assets/
"""

import os
import numpy as np
import librosa
import tensorflow as tf
from sklearn.model_selection import train_test_split

# ── Config ────────────────────────────────────────────────────────────────────
DATASET_DIR = "."       # Run from inside Audio_Dataset3/
SR          = 16000     # Sample rate expected by Android AudioRecord
DURATION    = 1.0       # Clip length in seconds
N_MFCC      = 40        # MFCC feature count
N_FRAMES    = 32        # Time frames per clip
EPOCHS      = 25
BATCH_SIZE  = 16
CONFIDENCE_THRESHOLD = 0.80  # Print a reminder for use in Android code
USE_ANDROID_COMPAT_FEATURES = True  # Match Android AudioPreprocessor.computeMFCC

OUTPUT_MODEL  = "keyword_spotting_model.tflite"
OUTPUT_LABELS = "keyword_labels.txt"

# ── Helpers ───────────────────────────────────────────────────────────────────
def pad_or_trim(y, target_len):
    if len(y) < target_len:
        y = np.pad(y, (0, target_len - len(y)))
    return y[:target_len]

def extract_mfcc(path):
    """Load a .wav and return MFCC features shaped (N_FRAMES, N_MFCC)."""
    y, _ = librosa.load(path, sr=SR, mono=True)
    y    = pad_or_trim(y, int(SR * DURATION))
    mfcc = librosa.feature.mfcc(y=y, sr=SR, n_mfcc=N_MFCC, n_fft=512, hop_length=512)
    # Resize to fixed frame count
    mfcc = librosa.util.fix_length(mfcc, size=N_FRAMES, axis=1)
    return mfcc.T  # (N_FRAMES, N_MFCC)

def normalize_audio_like_android(y):
    """Replicate Android dynamic normalization used in AudioPreprocessor."""
    max_abs = float(np.max(np.abs(y))) if len(y) > 0 else 0.0
    threshold = 1000.0 / 32767.0
    if max_abs > threshold:
        factor = max_abs
    else:
        factor = 1.0
    return y / factor if factor > 0 else y

def compute_band_energies_like_android(frame, num_bands):
    """Replicate AudioPreprocessor.computeBandEnergies in Kotlin."""
    energies = np.zeros(num_bands, dtype=np.float32)
    band_size = max(1, len(frame) // num_bands)
    for i in range(num_bands):
        start = i * band_size
        end = min(start + band_size, len(frame))
        if start >= len(frame) or start >= end:
            energies[i] = -10.0
            continue
        band = frame[start:end]
        energy = float(np.sum(np.square(band)))
        safe_energy = max(0.0, energy) + 1e-6
        log_energy = np.log(safe_energy)
        if np.isnan(log_energy) or np.isinf(log_energy):
            energies[i] = -10.0
        else:
            energies[i] = float(log_energy)
    return energies

def extract_android_compat_features(path):
    """Load a .wav and return Android-compatible features shaped (N_FRAMES, N_MFCC)."""
    y, _ = librosa.load(path, sr=SR, mono=True)
    y = pad_or_trim(y, int(SR * DURATION))
    y = normalize_audio_like_android(y)

    frame_size = max(1, len(y) // N_FRAMES)
    out = np.zeros((N_FRAMES, N_MFCC), dtype=np.float32)
    for f in range(N_FRAMES):
        start = f * frame_size
        end = min(start + frame_size, len(y))
        frame = y[start:end] if end > start else np.array([0.0], dtype=np.float32)
        out[f] = compute_band_energies_like_android(frame, N_MFCC)
    return out

# ── Load dataset ──────────────────────────────────────────────────────────────
LABELS = sorted([
    d for d in os.listdir(DATASET_DIR)
    if os.path.isdir(os.path.join(DATASET_DIR, d))
    and not d.startswith(".")
    and d not in ("venv", "__pycache__")                      # exclude non-data dirs
    and any(f.lower().endswith(".wav")                        # must contain wav files
            for f in os.listdir(os.path.join(DATASET_DIR, d)))
])
print(f"Found {len(LABELS)} classes: {LABELS}")

X, y = [], []
for label_idx, label in enumerate(LABELS):
    folder = os.path.join(DATASET_DIR, label)
    count = 0
    for fname in os.listdir(folder):
        if not fname.lower().endswith(".wav"):
            continue
        try:
            path = os.path.join(folder, fname)
            if USE_ANDROID_COMPAT_FEATURES:
                mfcc = extract_android_compat_features(path)
            else:
                mfcc = extract_mfcc(path)
            X.append(mfcc)
            y.append(label_idx)
            count += 1
        except Exception as e:
            print(f"  ⚠ Skipping {fname}: {e}")
    print(f"  ✓ {label}: {count} samples")

X = np.array(X)[..., np.newaxis]  # (N, N_FRAMES, N_MFCC, 1)
y = np.array(y)
feature_mode = "android_compat" if USE_ANDROID_COMPAT_FEATURES else "librosa_mfcc"
print(f"Feature mode: {feature_mode}")
print(f"\nDataset: {len(X)} total samples, shape {X.shape}")

# ── Train / val split ─────────────────────────────────────────────────────────
X_train, X_val, y_train, y_val = train_test_split(
    X, y, test_size=0.2, random_state=42, stratify=y
)
print(f"Train: {len(X_train)}, Val: {len(X_val)}")

# ── Model ─────────────────────────────────────────────────────────────────────
inputs = tf.keras.Input(shape=X.shape[1:])
x = tf.keras.layers.Conv2D(32, (3, 3), activation="relu", padding="same")(inputs)
x = tf.keras.layers.BatchNormalization()(x)
x = tf.keras.layers.MaxPooling2D((2, 2))(x)
x = tf.keras.layers.Conv2D(64, (3, 3), activation="relu", padding="same")(x)
x = tf.keras.layers.BatchNormalization()(x)
x = tf.keras.layers.MaxPooling2D((2, 2))(x)
x = tf.keras.layers.Flatten()(x)
x = tf.keras.layers.Dense(128, activation="relu")(x)
x = tf.keras.layers.Dropout(0.4)(x)
outputs = tf.keras.layers.Dense(len(LABELS), activation="softmax")(x)

model = tf.keras.Model(inputs, outputs)
model.compile(
    optimizer="adam",
    loss="sparse_categorical_crossentropy",
    metrics=["accuracy"]
)
model.summary()

callbacks = [
    tf.keras.callbacks.EarlyStopping(patience=5, restore_best_weights=True),
    tf.keras.callbacks.ReduceLROnPlateau(factor=0.5, patience=3)
]
history = model.fit(
    X_train, y_train,
    epochs=EPOCHS,
    batch_size=BATCH_SIZE,
    validation_data=(X_val, y_val),
    callbacks=callbacks
)

val_acc = max(history.history["val_accuracy"])
print(f"\n✅ Best val accuracy: {val_acc:.2%}")
if val_acc < 0.75:
    print("⚠  Val accuracy below 75%. Consider recording more diverse samples.")

# ── Export TFLite ─────────────────────────────────────────────────────────────
converter     = tf.lite.TFLiteConverter.from_keras_model(model)
tflite_bytes  = converter.convert()
with open(OUTPUT_MODEL, "wb") as f:
    f.write(tflite_bytes)
print(f"✅ Saved: {OUTPUT_MODEL} ({len(tflite_bytes) / 1024:.1f} KB)")

# ── Export labels ─────────────────────────────────────────────────────────────
with open(OUTPUT_LABELS, "w") as f:
    f.write("\n".join(LABELS))
print(f"✅ Saved: {OUTPUT_LABELS}")
print(f"\nLabels: {LABELS}")
print(f"\n📲 Next steps:")
print(f"  1. Copy '{OUTPUT_MODEL}' → app/src/main/assets/")
print(f"  2. Copy '{OUTPUT_LABELS}' → app/src/main/assets/")
print(f"  3. Rebuild the app — the TFLite module will load automatically.")
print(f"  4. Use confidence threshold: {CONFIDENCE_THRESHOLD}")

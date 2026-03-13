import os
import numpy as np
import librosa
import tensorflow as tf
from collections import defaultdict

ROOT = r"c:\Users\elson\OneDrive\Documents\SafePulse\Audio_Dataset3"
MODEL = os.path.join(ROOT, "keyword_spotting_model.tflite")
LABELS_PATH = os.path.join(ROOT, "keyword_labels.txt")

SR = 16000
DURATION = 1.0
N_MFCC = 40
N_FRAMES = 32

with open(LABELS_PATH, "r", encoding="utf-8") as f:
    labels = [x.strip() for x in f if x.strip()]


def pad_or_trim(y, target_len):
    if len(y) < target_len:
        y = np.pad(y, (0, target_len - len(y)))
    return y[:target_len]

def normalize_audio_like_android(y):
    max_abs = float(np.max(np.abs(y))) if len(y) > 0 else 0.0
    threshold = 1000.0 / 32767.0
    factor = max_abs if max_abs > threshold else 1.0
    return y / factor if factor > 0 else y

def compute_band_energies_like_android(frame, num_bands):
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
        energies[i] = -10.0 if (np.isnan(log_energy) or np.isinf(log_energy)) else float(log_energy)
    return energies

def extract_android_compat_features(path):
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
    return out.astype(np.float32)

interpreter = tf.lite.Interpreter(model_path=MODEL)
interpreter.allocate_tensors()
input_details = interpreter.get_input_details()[0]
output_details = interpreter.get_output_details()[0]

per_class = defaultdict(lambda: {"ok": 0, "total": 0})
confidences = []

for true_label in labels:
    class_dir = os.path.join(ROOT, true_label)
    if not os.path.isdir(class_dir):
        continue
    for fn in os.listdir(class_dir):
        if not fn.lower().endswith('.wav'):
            continue
        wav = os.path.join(class_dir, fn)
        feat = extract_android_compat_features(wav)[np.newaxis, ..., np.newaxis]
        interpreter.set_tensor(input_details['index'], feat)
        interpreter.invoke()
        probs = interpreter.get_tensor(output_details['index'])[0]
        pred_idx = int(np.argmax(probs))
        pred_label = labels[pred_idx]
        conf = float(np.max(probs))
        confidences.append(conf)
        per_class[true_label]['total'] += 1
        if pred_label == true_label:
            per_class[true_label]['ok'] += 1

correct = sum(v['ok'] for v in per_class.values())
total = sum(v['total'] for v in per_class.values())
acc = (correct / total) if total else 0.0
print(f"TOTAL={total} CORRECT={correct} ACC={acc:.4f}")
for k in sorted(per_class.keys()):
    v = per_class[k]
    a = (v['ok']/v['total']) if v['total'] else 0.0
    print(f"CLASS {k}: {v['ok']}/{v['total']} ({a:.4f})")
if confidences:
    arr = np.array(confidences)
    print(f"CONF mean={arr.mean():.4f} min={arr.min():.4f} p10={np.percentile(arr,10):.4f}")

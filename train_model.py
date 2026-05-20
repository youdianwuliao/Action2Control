#!/usr/bin/env python3
"""
动作分类模型训练脚本

功能:
1. 使用 MediaPipe Holistic 从视频数据中提取姿态关键点
2. 训练分类模型 (基于 TensorFlow/Keras)
3. 将训练好的模型转换为 TFLite 格式

支持的 5 种动作标签:
- swipe_up:   上滑 (刷下一个视频)
- swipe_down: 下滑 (刷上一个视频)
- like:       点赞
- back:       返回
- unknown:    未知动作

数据收集流程:
1. 录制每个动作的视频样本 (建议每个动作 20-50 个视频)
2. 将视频按动作类别放入对应目录:
   data/
   ├── swipe_up/
   │   ├── video_001.mp4
   │   └── video_002.mp4
   ├── swipe_down/
   ├── like/
   ├── back/
   └── unknown/

使用方法:
    # 1. 安装依赖
    pip install tensorflow mediapipe opencv-python numpy scikit-learn

    # 2. 准备数据集 (按上述目录结构放置视频)

    # 3. 运行训练
    python train_model.py --data_dir ./data --output action_model.tflite

    # 4. 将生成的模型复制到 Android 项目
    cp action_model.tflite <项目路径>/app/src/main/assets/
"""

import argparse
import os
import sys
from pathlib import Path

import cv2
import mediapipe as mp
import numpy as np

try:
    import tensorflow as tf
except ImportError:
    print("错误: 需要安装 TensorFlow")
    print("请运行: pip install tensorflow")
    sys.exit(1)

from sklearn.model_selection import train_test_split
from sklearn.preprocessing import LabelEncoder

# 动作标签定义
ACTION_LABELS = ["swipe_up", "swipe_down", "like", "back", "unknown"]

# MediaPipe Holistic 初始化
mp_holistic = mp.solutions.holistic
mp_drawing = mp.solutions.drawing_utils

# 姿态关键点数量 (MediaPipe Pose 有 33 个关键点)
NUM_KEYPOINTS = 33
NUM_COORDS_PER_KEYPOINT = 3  # x, y, z
FEATURE_DIM = NUM_KEYPOINTS * NUM_COORDS_PER_KEYPOINT  # 99


def extract_keypoints_from_video(video_path: str, max_frames: int = 30) -> np.ndarray:
    """
    从单个视频文件中提取姿态关键点

    使用 MediaPipe Holistic 逐帧检测，取所有帧的平均值作为该视频的特征向量。

    Args:
        video_path: 视频文件路径
        max_frames: 最大处理帧数

    Returns:
        形状为 (FEATURE_DIM,) 的特征向量，即 99 维浮点数组
    """
    cap = cv2.VideoCapture(video_path)
    if not cap.isOpened():
        print(f"  警告: 无法打开视频 {video_path}")
        return np.zeros(FEATURE_DIM, dtype=np.float32)

    all_keypoints = []
    frame_count = 0

    with mp_holistic.Holistic(
        static_image_mode=False,
        model_complexity=1,
        smooth_landmarks=True,
        min_detection_confidence=0.5,
        min_tracking_confidence=0.5
    ) as holistic:

        while frame_count < max_frames:
            ret, frame = cap.read()
            if not ret:
                break

            # 转换颜色空间 (MediaPipe 需要 RGB)
            image_rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            results = holistic.process(image_rgb)

            # 提取 Pose 关键点
            if results.pose_landmarks:
                keypoints = []
                for landmark in results.pose_landmarks.landmark:
                    keypoints.extend([landmark.x, landmark.y, landmark.z])
                all_keypoints.append(keypoints)

            frame_count += 1

    cap.release()

    if not all_keypoints:
        return np.zeros(FEATURE_DIM, dtype=np.float32)

    # 对所有帧的关键点取平均值，得到该视频的单一特征向量
    avg_keypoints = np.mean(all_keypoints, axis=0).astype(np.float32)
    return avg_keypoints


def load_dataset(data_dir: str) -> tuple:
    """
    从目录结构加载数据集

    目录结构应为:
        data_dir/
        ├── swipe_up/
        ├── swipe_down/
        ├── like/
        ├── back/
        └── unknown/

    Args:
        data_dir: 数据集根目录

    Returns:
        (features, labels) 特征矩阵和标签列表
    """
    features = []
    labels = []

    data_path = Path(data_dir)
    if not data_path.exists():
        print(f"错误: 数据目录不存在: {data_dir}")
        sys.exit(1)

    for action_name in ACTION_LABELS:
        action_dir = data_path / action_name
        if not action_dir.exists():
            print(f"警告: 跳过不存在的类别目录: {action_dir}")
            continue

        video_files = list(action_dir.glob("*.mp4")) + list(action_dir.glob("*.avi")) + list(action_dir.glob("*.mov"))
        if not video_files:
            print(f"警告: {action_name} 目录下没有视频文件")
            continue

        print(f"\n处理类别: {action_name} ({len(video_files)} 个视频)")
        for i, video_file in enumerate(sorted(video_files)):
            print(f"  [{i+1}/{len(video_files)}] {video_file.name}")
            feature_vector = extract_keypoints_from_video(str(video_file))
            features.append(feature_vector)
            labels.append(action_name)

    if not features:
        print("错误: 未找到任何有效数据")
        sys.exit(1)

    return np.array(features, dtype=np.float32), labels


def build_model(input_dim: int, num_classes: int) -> tf.keras.Model:
    """
    构建分类模型

    架构: 输入 -> Dense(128) -> Dropout -> Dense(64) -> Dropout -> Dense(num_classes)

    Args:
        input_dim: 输入特征维度 (99)
        num_classes: 输出类别数 (5)

    Returns:
        编译好的 Keras 模型
    """
    model = tf.keras.Sequential([
        tf.keras.layers.Input(shape=(input_dim,), name="input_landmarks"),
        tf.keras.layers.Dense(128, activation="relu", name="hidden_1"),
        tf.keras.layers.Dropout(0.3, name="dropout_1"),
        tf.keras.layers.Dense(64, activation="relu", name="hidden_2"),
        tf.keras.layers.Dropout(0.3, name="dropout_2"),
        tf.keras.layers.Dense(32, activation="relu", name="hidden_3"),
        tf.keras.layers.Dense(num_classes, activation="softmax", name="output_actions")
    ])

    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=0.001),
        loss="sparse_categorical_crossentropy",
        metrics=["accuracy"]
    )

    return model


def convert_to_tflite(model: tf.keras.Model, output_path: str) -> None:
    """
    将 Keras 模型转换为 TFLite 格式

    使用动态范围量化优化模型大小。

    Args:
        model: 训练好的 Keras 模型
        output_path: 输出文件路径
    """
    print(f"\n转换为 TFLite 格式...")

    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]

    tflite_model = converter.convert()

    with open(output_path, "wb") as f:
        f.write(tflite_model)

    print(f"  模型已保存到: {output_path}")
    print(f"  文件大小: {len(tflite_model) / 1024:.2f} KB")


def train_model(data_dir: str, output_path: str, epochs: int = 50, batch_size: int = 16) -> None:
    """
    完整的训练流程

    步骤:
    1. 加载数据集
    2. 编码标签
    3. 划分训练/验证集
    4. 构建模型
    5. 训练模型
    6. 评估模型
    7. 转换为 TFLite

    Args:
        data_dir: 数据集目录
        output_path: TFLite 模型输出路径
        epochs: 训练轮数
        batch_size: 批次大小
    """
    print("=" * 60)
    print("动作分类模型训练")
    print("=" * 60)

    # 步骤 1: 加载数据集
    print("\n[步骤 1/6] 加载数据集...")
    features, labels = load_dataset(data_dir)
    print(f"  总样本数: {len(features)}")
    print(f"  特征维度: {features.shape[1]}")

    # 步骤 2: 编码标签
    print("\n[步骤 2/6] 编码标签...")
    label_encoder = LabelEncoder()
    label_encoder.fit(ACTION_LABELS)
    encoded_labels = label_encoder.transform(labels)
    print(f"  标签映射: {dict(zip(label_encoder.classes_, label_encoder.transform(label_encoder.classes_)))}")

    # 步骤 3: 划分训练/验证集
    print("\n[步骤 3/6] 划分训练/验证集...")
    X_train, X_val, y_train, y_val = train_test_split(
        features, encoded_labels, test_size=0.2, random_state=42, stratify=encoded_labels
    )
    print(f"  训练集: {len(X_train)} 样本")
    print(f"  验证集: {len(X_val)} 样本")

    # 步骤 4: 构建模型
    print("\n[步骤 4/6] 构建模型...")
    model = build_model(input_dim=FEATURE_DIM, num_classes=len(ACTION_LABELS))
    model.summary()

    # 步骤 5: 训练模型
    print(f"\n[步骤 5/6] 训练模型 ({epochs} epochs)...")
    early_stop = tf.keras.callbacks.EarlyStopping(
        monitor="val_loss", patience=10, restore_best_weights=True
    )

    history = model.fit(
        X_train, y_train,
        validation_data=(X_val, y_val),
        epochs=epochs,
        batch_size=batch_size,
        callbacks=[early_stop],
        verbose=1
    )

    # 步骤 6: 评估模型
    print("\n[步骤 6/6] 评估模型...")
    val_loss, val_accuracy = model.evaluate(X_val, y_val, verbose=0)
    print(f"  验证集准确率: {val_accuracy:.4f}")
    print(f"  验证集损失:   {val_loss:.4f}")

    # 转换为 TFLite
    convert_to_tflite(model, output_path)

    # 保存标签映射 (供 Android 端参考)
    label_map_path = output_path.replace(".tflite", "_labels.txt")
    with open(label_map_path, "w") as f:
        for label in ACTION_LABELS:
            f.write(f"{label}\n")
    print(f"  标签文件已保存到: {label_map_path}")

    print("\n" + "=" * 60)
    print("训练完成!")
    print(f"模型已保存到: {output_path}")
    print("下一步: 将 .tflite 文件复制到 Android 项目的 app/src/main/assets/ 目录")
    print("=" * 60)


def main():
    parser = argparse.ArgumentParser(
        description="训练动作分类模型并导出为 TFLite 格式",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例:
  python train_model.py --data_dir ./data --output action_model.tflite
  python train_model.py --data_dir ./data --output action_model.tflite --epochs 100 --batch_size 32

数据目录结构:
  data/
  ├── swipe_up/
  ├── swipe_down/
  ├── like/
  ├── back/
  └── unknown/
        """
    )

    parser.add_argument(
        "--data_dir",
        type=str,
        required=True,
        help="数据集根目录 (包含各动作类别的子目录)"
    )
    parser.add_argument(
        "--output",
        type=str,
        default="action_model.tflite",
        help="输出 TFLite 模型文件路径 (默认: action_model.tflite)"
    )
    parser.add_argument(
        "--epochs",
        type=int,
        default=50,
        help="训练轮数 (默认: 50)"
    )
    parser.add_argument(
        "--batch_size",
        type=int,
        default=16,
        help="批次大小 (默认: 16)"
    )

    args = parser.parse_args()

    train_model(
        data_dir=args.data_dir,
        output_path=args.output,
        epochs=args.epochs,
        batch_size=args.batch_size
    )


if __name__ == "__main__":
    main()

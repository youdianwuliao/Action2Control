#!/usr/bin/env python3
"""
生成占位 TFLite 模型文件

用途: 在真实训练数据准备好之前，提供一个结构正确的占位模型，
      让 Android 应用能够正常加载和运行分类流程。

输入形状: [1, 99]  (33 关键点 × 3 坐标 [x, y, z])
输出形状: [1, 5]  (5 种动作概率: swipe_up, swipe_down, like, back, unknown)

使用方法:
    python create_placeholder_model.py

输出:
    action_model.tflite  (复制到 Android 项目的 app/src/main/assets/ 目录)
"""

import numpy as np

try:
    import tensorflow as tf
except ImportError:
    print("错误: 需要安装 TensorFlow")
    print("请运行: pip install tensorflow")
    exit(1)


def create_placeholder_model(output_path="action_model.tflite"):
    """创建一个简单的占位 TFLite 模型"""

    print("正在创建占位 TFLite 模型...")
    print(f"  输入形状: [1, 99]")
    print(f"  输出形状: [1, 5]")

    # 构建一个简单的 Sequential 模型
    model = tf.keras.Sequential([
        tf.keras.layers.Input(shape=(99,), name="input_landmarks"),
        tf.keras.layers.Dense(64, activation="relu", name="hidden_1"),
        tf.keras.layers.Dense(32, activation="relu", name="hidden_2"),
        tf.keras.layers.Dense(5, activation="softmax", name="output_actions")
    ])

    model.compile(
        optimizer="adam",
        loss="sparse_categorical_crossentropy",
        metrics=["accuracy"]
    )

    # 生成随机数据进行一次前向传播，确保权重已初始化
    dummy_input = np.random.random((1, 99)).astype(np.float32)
    _ = model.predict(dummy_input, verbose=0)
    print("  模型已初始化随机权重")

    # 转换为 TFLite 格式
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    tflite_model = converter.convert()

    # 保存模型文件
    with open(output_path, "wb") as f:
        f.write(tflite_model)

    print(f"  模型已保存到: {output_path}")
    print(f"  文件大小: {len(tflite_model) / 1024:.2f} KB")
    print("\n下一步:")
    print("  将 action_model.tflite 复制到 Android 项目的 app/src/main/assets/ 目录")
    print("  命令: cp action_model.tflite <项目路径>/app/src/main/assets/")

    return output_path


if __name__ == "__main__":
    create_placeholder_model()

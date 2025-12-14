# prompt-reader-kotlin

这是一份“可移植到 Android 的 Kotlin 核心解析库”骨架，用于对齐本仓库 Python 参考实现（Stable Diffusion Prompt Reader）。

目标（按你要求）：
- 全格式解析：A1111 / ComfyUI / SwarmUI / Fooocus / NovelAI(legacy+stealth) / EasyDiffusion / InvokeAI / DrawThings
- Windows GUI 的功能不缺：读取、导出、清除、编辑保存（写回元数据）

注意：
- `:core` 尽量只放“平台无关逻辑”。
- JPEG/WEBP 的 EXIF 读写在 Android 端建议用 `androidx.exifinterface.media.ExifInterface`；这里用接口抽象，CLI 只实现 PNG 相关。

## 和 Python 对齐（fixtures）

1) 用 Python 生成 fixtures：

```bash
python comfyui-workflow-prompt-viewer/tools/generate_fixtures.py -i <样本目录> -o <out> --pretty
```

2) Kotlin 端对同一批图片跑 `:cli` 输出 JSON，再 diff。

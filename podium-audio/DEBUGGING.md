# 调试指南 / Debugging Guide

## JVM Desktop 日志输出 / JVM Desktop Logging

### 查看日志 / Viewing Logs

运行 JVM 应用时，Rust 日志会自动输出到控制台：

```bash
./gradlew jvmRun
```

### 日志格式 / Log Format

日志使用带颜色的格式，包含：
- **时间戳**（毫秒精度）
- **日志级别**（颜色编码）
  - 🔴 ERROR - 红色
  - 🟡 WARN - 黄色
  - 🟢 INFO - 绿色
  - 🔵 DEBUG - 蓝色
- **文件名和行号**
- **消息内容**

示例：
```
🎵 Podium Audio Desktop logging initialized
[2025-12-05 14:30:15.123 INFO player.rs:76] 🧵 Decode thread started for URL: https://example.com/audio.mp3
[2025-12-05 14:30:15.234 INFO player.rs:182] ✅ Network source created successfully
[2025-12-05 14:30:15.456 INFO player.rs:211] 📊 Track info: sample_rate=44100Hz, channels=2, duration=180000ms
[2025-12-05 14:30:15.567 INFO player.rs:133] ⏳ Waiting for prebuffer... (need 24000 samples)
[2025-12-05 14:30:16.123 DEBUG player.rs:144] 📊 Buffering progress: 12000/24000 samples (50.0%)
[2025-12-05 14:30:16.678 INFO player.rs:140] ✅ Prebuffer complete: 24500 samples available
```

### 日志符号说明 / Log Symbols

| 符号 | 含义 |
|-----|------|
| 🎵 | 日志系统初始化 |
| 🧵 | 解码线程 |
| 📡 | 网络操作 |
| 🎬 | 解封装器 |
| 📊 | 统计信息 |
| 🔄 | 重采样/循环 |
| ⏳ | 等待/加载中 |
| ✅ | 成功 |
| ❌ | 失败 |
| ⚠️  | 警告 |
| 📭 | 流结束 |
| 📈 | 最终统计 |
| ⏹️  | 停止 |

### 调试播放问题 / Debugging Playback Issues

#### 1. 检查网络连接 / Check Network Connection

如果看到：
```
❌ Failed to create network source: ...
```

**问题**：无法连接到 URL 或下载失败
**解决**：检查网络连接和 URL 是否有效

#### 2. 检查预缓冲 / Check Prebuffering

如果看到：
```
⚠️  Prebuffer timeout! Only 1000 samples available (need 24000)
```

**问题**：下载速度太慢或解码失败
**原因可能**：
- 网络速度慢
- 服务器响应慢
- 音频格式不支持
- 解封装或解码失败

**查看解码线程日志**：找到 "🧵 Decode thread" 开头的日志，看是否有错误

#### 3. 检查解码进度 / Check Decode Progress

正常情况下应该看到：
```
🔄 Starting decode loop...
🎵 Decoded 100 packets, 240000 samples, buffer: 96000 samples
🎵 Decoded 200 packets, 480000 samples, buffer: 96000 samples
...
```

如果没有看到解码进度日志，说明：
- 解封装失败（检查 "🎬 Creating demuxer" 后是否有错误）
- 解码器创建失败（检查 "🎵 Creating audio decoder" 后是否有错误）
- 音频格式不支持

#### 4. 一直处于 Loading 状态 / Stuck in Loading State

**症状**：`loadUrl()` 调用一直不返回

**检查日志顺序**：
1. ✅ 应该看到 "🧵 Decode thread started"
2. ✅ 应该看到 "✅ Network source created"
3. ✅ 应该看到 "✅ Demuxer created"
4. ✅ 应该看到 "📊 Track info"
5. ✅ 应该看到 "🔄 Starting decode loop"
6. ✅ 应该看到 "📊 Buffering progress" 或 "✅ Prebuffer complete"

**如果卡在某一步**：
- 卡在步骤 2：网络问题
- 卡在步骤 3：音频格式识别失败
- 卡在步骤 4：无法获取音频信息
- 卡在步骤 6：解码速度太慢或失败

### 增加日志详细度 / Increase Log Verbosity

默认日志级别是 `DEBUG`。如果需要更详细的日志，可以设置环境变量：

```bash
export RUST_LOG=trace
./gradlew jvmRun
```

或在一行中：
```bash
RUST_LOG=trace ./gradlew jvmRun
```

### 只看特定模块的日志 / Filter Logs by Module

```bash
# 只看播放器日志
RUST_LOG=player=debug ./gradlew jvmRun

# 只看解封装日志
RUST_LOG=podium_demux_symphonia=debug ./gradlew jvmRun

# 只看网络日志
RUST_LOG=podium_transport_http=debug ./gradlew jvmRun

# 组合多个模块
RUST_LOG=player=debug,podium_demux_symphonia=debug ./gradlew jvmRun
```

## 常见问题 / Common Issues

### 1. 没有日志输出 / No Logs Appear

**原因**：日志输出到 stderr，可能被重定向了

**解决**：检查 Gradle 配置，确保 stderr 输出到控制台

### 2. 日志乱码 / Garbled Logs

**原因**：终端不支持颜色或 UTF-8

**解决**：使用支持 UTF-8 和 ANSI 颜色的终端（如 iTerm2, Terminal.app, GNOME Terminal）

### 3. 某些 URL 一直加载 / Some URLs Keep Loading

**可能原因**：
1. **服务器不支持 Range 请求**
   - 日志中查找 "Range request" 相关错误
   - 某些 CDN 或服务器配置问题

2. **音频格式不支持**
   - 查看 "📊 Track info" 日志
   - 支持的格式：MP3, AAC, FLAC, OGG Vorbis, WAV

3. **重采样失败**
   - 查看是否有 "🔄 Creating resampler" 日志
   - 查看采样率和声道配置

## 性能监控 / Performance Monitoring

解码线程会每 100 个包输出一次统计：
```
🎵 Decoded 100 packets, 240000 samples, buffer: 96000 samples
```

- **packets**：已解码的音频包数量
- **samples**：已解码的样本总数
- **buffer**：环形缓冲区中可用的样本数

**正常情况**：
- buffer 应该保持在 24000 以上（0.5 秒缓冲）
- 如果 buffer 一直很低（< 10000），说明解码速度跟不上播放速度

## 提交 Bug 报告 / Filing Bug Reports

如果遇到问题，请提供：
1. 完整的日志输出（从 "🎵 Podium Audio Desktop logging initialized" 开始）
2. 音频 URL（如果可以公开）
3. 音频格式信息（从 "📊 Track info" 日志中获取）
4. 系统信息（macOS/Linux/Windows，版本）
5. 问题描述（什么时候卡住？是否有错误消息？）

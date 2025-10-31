# 订阅导入导出功能

## 概述

Podium 现在支持多种标准格式的订阅导入和导出功能，包括：

- **OPML 2.0** - 播客订阅的标准格式，与大多数播客应用兼容
- **JSON** - 包含更多元数据的格式，适合备份和迁移

## 功能特性

### 导入功能

1. **文件选择器** - 直接从文件系统选择文件（JVM平台）
2. **自动格式检测** - 自动识别 OPML 或 JSON 格式
3. **批量导入** - 一次性导入多个订阅
4. **重复检测** - 自动跳过已订阅的播客
5. **错误处理** - 显示导入失败的订阅及原因
6. **元数据保留** - JSON 格式可以保留自动下载等设置

### 导出功能

1. **文件保存** - 直接保存到文件系统（JVM平台）
2. **格式选择** - 可选择 OPML 或 JSON 格式
3. **完整元数据** - 包含标题、描述、封面等信息
4. **标准兼容** - OPML 格式与其他播客应用兼容
5. **即时切换** - 可以在对话框中即时切换导出格式
6. **剪贴板支持** - 可复制到剪贴板手动保存

## 使用方法

### 导入订阅

#### 方式一：从文件导入（推荐 - JVM平台）
1. 打开"我的"标签页
2. 点击"导入订阅"
3. 点击"从文件选择"按钮
4. 在文件选择器中选择 .opml、.xml 或 .json 文件
5. 文件内容将自动加载到文本框
6. 点击"导入"按钮
7. 查看导入结果统计

#### 方式二：粘贴内容导入（所有平台）
1. 打开"我的"标签页
2. 点击"导入订阅"
3. 将 OPML 或 JSON 内容粘贴到文本框
4. 点击"导入"按钮
5. 查看导入结果统计

### 导出订阅

#### 方式一：保存到文件（推荐 - JVM/Android）
1. 打开"我的"标签页
2. 点击"导出订阅"
3. 在格式下拉菜单中选择 OPML 或 JSON
4. 等待内容生成
5. 点击"保存到文件"按钮
6. **JVM**: 在文件保存对话框中选择保存位置
   **Android**: 文件自动保存到下载文件夹，显示 Toast 提示
7. 文件将自动命名为 `podium_subscriptions.opml` 或 `podium_subscriptions.json`

#### 方式二：复制内容（所有平台）
1. 打开"我的"标签页
2. 点击"导出订阅"
3. 在格式下拉菜单中选择 OPML 或 JSON
4. 等待内容生成
5. 点击"复制"按钮将内容复制到剪贴板
6. 手动创建文件并粘贴内容，或分享给其他应用

## 格式说明

### OPML 格式

OPML (Outline Processor Markup Language) 是播客订阅的标准格式。

**示例：**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<opml version="2.0">
  <head>
    <title>Podium Subscriptions</title>
    <dateCreated>2024-10-31T10:00:00Z</dateCreated>
  </head>
  <body>
    <outline type="rss" text="播客名称" title="播客名称" xmlUrl="https://example.com/feed.xml" description="播客描述" imageUrl="https://example.com/cover.jpg" />
  </body>
</opml>
```

**优点：**
- 标准格式，与大多数播客应用兼容
- 文件体积小
- 易于阅读和编辑

**支持的属性：**
- `title/text` - 播客标题
- `xmlUrl` - RSS 订阅地址
- `description` - 播客描述
- `imageUrl` - 封面图片地址

### JSON 格式

JSON 格式包含更丰富的元数据，适合完整备份。

**示例：**

```json
{
  "version": "1.0",
  "exportedAt": "2024-10-31T10:00:00Z",
  "subscriptions": [
    {
      "title": "播客名称",
      "feedUrl": "https://example.com/feed.xml",
      "description": "播客描述",
      "artworkUrl": "https://example.com/cover.jpg",
      "lastUpdated": "2024-10-31T09:00:00Z",
      "autoDownload": false
    }
  ]
}
```

**优点：**
- 包含更多元数据
- 可以保留应用特定设置（如自动下载）
- 易于程序处理
- 结构清晰

**支持的字段：**
- `title` - 播客标题
- `feedUrl` - RSS 订阅地址
- `description` - 播客描述
- `artworkUrl` - 封面图片地址
- `lastUpdated` - 最后更新时间
- `autoDownload` - 是否启用自动下载

## 实现细节

### 架构

```
data/subscription/
├── SubscriptionExporter.kt  - 导出服务
└── SubscriptionImporter.kt  - 导入服务

data/repository/
└── PodcastRepository.kt     - 订阅管理（已集成导入导出）

presentation/
└── PodiumController.kt      - 控制器（已集成导入导出）

platform/
├── FileOperations.kt        - 跨平台文件操作接口
├── FileOperations.jvm.kt    - JVM 平台实现
├── FileOperations.android.kt - Android 平台实现（待完善）
└── FileOperations.ios.kt    - iOS 平台实现（待完善）

ui/profile/
├── OpmlDialogs.kt          - 导入导出对话框（已更新，包含文件按钮）
└── ProfileScreen.kt        - 个人页面（已更新）
```

### 关键类

#### SubscriptionExporter

负责将订阅列表导出为不同格式：

```kotlin
class SubscriptionExporter {
    fun exportAsOpml(podcasts: List<Podcast>): String
    fun exportAsJson(podcasts: List<Podcast>): String
}
```

#### SubscriptionImporter

负责从不同格式导入订阅：

```kotlin
class SubscriptionImporter {
    fun import(content: String): ImportResult
    fun importFromOpml(opml: String): List<FeedInfo>
    fun importFromJson(json: String): List<FeedInfo>
}
```

#### FileOperations

跨平台文件操作接口：

```kotlin
interface FileOperations {
    suspend fun pickFileToImport(): String?
    suspend fun saveToFile(
        content: String,
        suggestedFileName: String,
        mimeType: String = "text/plain"
    ): Boolean
}

expect fun createFileOperations(context: PlatformContext): FileOperations
```

**平台实现状态：**
- ✅ **JVM**: 完整实现，使用 `FileDialog` 提供原生文件选择器
- ✅ **Android**: 保存到下载文件夹实现（无需权限）
  - Android 10+: 使用 `MediaStore` API
  - Android 9-: 使用传统 Downloads 目录
  - 包含 Toast 提示
- ⚠️ **iOS**: 基础实现，需要 `UIDocumentPickerViewController` 集成

## 向后兼容

为了保持向后兼容，保留了原有的方法：

```kotlin
// 仍然可用
suspend fun importOpml(opml: String): OpmlImportResult
suspend fun exportOpml(): String

// 推荐使用新方法
suspend fun importSubscriptions(content: String): OpmlImportResult
suspend fun exportSubscriptions(format: ExportFormat): String
```

## 平台支持

| 功能 | JVM | Android | iOS |
|------|-----|---------|-----|
| 文件选择器导入 | ✅ | ⚠️ | ⚠️ |
| 文件保存到下载 | ✅ | ✅ | ⚠️ |
| 粘贴内容导入 | ✅ | ✅ | ✅ |
| 复制到剪贴板 | ✅ | ✅ | ✅ |
| OPML 格式 | ✅ | ✅ | ✅ |
| JSON 格式 | ✅ | ✅ | ✅ |
| Toast/通知提示 | ❌ | ✅ | ⚠️ |

**说明：**
- ✅ = 完整实现并测试
- ⚠️ = 基础实现，需要进一步集成平台特定 API
- ❌ = 暂不支持

**Android 特性：**
- 保存文件到系统下载文件夹
- 无需申请存储权限
- Android 10+ 使用 MediaStore API（推荐）
- Android 9- 使用传统文件系统
- 操作完成后显示 Toast 提示

## 未来增强

- [ ] 完善 Android 平台的文件选择器（使用 Activity Result API）
- [ ] 完善 iOS 平台的文件选择器（使用 UIDocumentPickerViewController）
- [ ] 更多格式支持（如 Podcast Index）
- [ ] 导入进度显示
- [ ] 批量编辑导入的订阅
- [ ] 自动备份到云端
- [ ] 定期自动导出

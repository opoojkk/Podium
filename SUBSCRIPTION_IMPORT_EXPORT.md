# 订阅导入导出功能

## 概述

Podium 现在支持多种标准格式的订阅导入和导出功能，包括：

- **OPML 2.0** - 播客订阅的标准格式，与大多数播客应用兼容
- **JSON** - 包含更多元数据的格式，适合备份和迁移

## 功能特性

### 导入功能

1. **自动格式检测** - 自动识别 OPML 或 JSON 格式
2. **批量导入** - 一次性导入多个订阅
3. **重复检测** - 自动跳过已订阅的播客
4. **错误处理** - 显示导入失败的订阅及原因
5. **元数据保留** - JSON 格式可以保留自动下载等设置

### 导出功能

1. **格式选择** - 可选择 OPML 或 JSON 格式
2. **完整元数据** - 包含标题、描述、封面等信息
3. **标准兼容** - OPML 格式与其他播客应用兼容
4. **即时切换** - 可以在对话框中即时切换导出格式

## 使用方法

### 导入订阅

1. 打开"我的"标签页
2. 点击"导入订阅"
3. 粘贴 OPML 或 JSON 内容
4. 点击"导入"按钮
5. 查看导入结果

### 导出订阅

1. 打开"我的"标签页
2. 点击"导出订阅"
3. 选择导出格式（OPML 或 JSON）
4. 复制生成的内容
5. 保存到文件或分享

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

ui/profile/
├── OpmlDialogs.kt          - 导入导出对话框（已更新）
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

## 未来增强

- [ ] 文件选择器支持（直接从文件导入）
- [ ] 文件保存支持（直接保存到文件）
- [ ] 更多格式支持（如 Podcast Index）
- [ ] 导入进度显示
- [ ] 批量编辑导入的订阅

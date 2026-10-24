# xtt的工具箱

xtt的工具箱是一款以**本地个人阅读、写作为核心**的 Android 工具箱，面向离线使用和长期维护的个人工作流。

公开源码仓库：

<https://github.com/Xtt-no-name/xtt_tool>

> 本项目是公开源码项目（source-available），采用 **PolyForm Noncommercial License 1.0.0**，**不是 OSI 认可的开源软件**。商业用途受到限制，详见[许可证](#许可证)。

## 当前功能

### 阅读

- 本地阅读书架，导入并阅读 TXT、EPUB 和 Markdown 文件；
- 章节识别、目录导航、阅读进度保存与恢复；
- 书签、段落评论和章节评论；
- 阅读内容搜索、搜索历史；
- 字号、行距、段间距、左右边距和主题设置；
- 封面展示、导入进度、批量导入和书籍删除。

### 写作

- 本地写作书架，可新建作品、导入 TXT/EPUB、从阅读书架建立可编辑副本；
- 分卷和章节管理，支持新建、重命名、排序、移动和删除；
- 写作编辑器：基础格式、标题、引用、粗体、斜体、图片、常用标点；
- 智能排版、查找与替换、撤销/重做、自动保存和保存状态；
- 字数统计（可设置不计标点符号）；
- 每日写作净新增统计和写作热力图；
- 作品导出为 TXT 或 EPUB，以及本地备份/恢复。

### 个人与设置

- 本地昵称、头像等个人资料；
- 主题模式、阅读设置和编辑器设置；
- 搜索历史开关、图片 Markdown 目录选择等本地设置。

## 支持的文件格式

| 格式 | 阅读 | 写作导入 | 导出 |
|---|---|---|---|
| TXT | 支持 | 支持 | 支持 |
| EPUB | 支持 | 支持 | 支持 |
| Markdown | 支持 | 不支持 | 暂未提供 |

Markdown 和 EPUB 的解析范围以项目当前实现为准，不保证兼容所有扩展语法或所有外部样式。

## 数据与隐私

- 应用数据主要保存在 Android 应用私有目录：
  - SQLite 数据库：`xtt_library.db`；
  - 导入的正文和书籍资源：应用内部 `files/library`；
  - 头像、设置和搜索历史：应用私有文件与 SharedPreferences。
- 导入时使用应用内部副本，不覆盖外部原始文件。
- 应用不需要账号，也不提供云同步服务；当前没有应用内云端上传功能。
- Android 系统自动备份可能受设备设置影响（Manifest 当前启用 `allowBackup`），因此“本地存储”不等于绝对不会离开设备。
- 本项目不宣称提供绝对安全保护；请自行备份重要作品。

## 开发环境

以仓库当前配置为准：

- 应用包名：`com.xtt.toolbox`
- 版本：`versionCode = 1`，`versionName = 1.0`
- Android Gradle Plugin：`9.4.1`
- Gradle Wrapper：`9.6.0`
- Kotlin：`2.2.10`
- Compose BOM：`2026.02.01`
- `compileSdk` / `targetSdk`：`37`
- `minSdk`：`24`
- Java 源码/字节码兼容级别：`11`
- 当前开发环境使用 OpenJDK `25.0.3`；Gradle 构建需要兼容的 JDK 17+ 环境

## Windows 构建

项目自带 Gradle Wrapper。进入项目根目录后执行：

```powershell
.\gradlew.bat assembleDebug
```

运行 JVM 单元测试：

```powershell
.\gradlew.bat testDebugUnitTest
```

运行 Android 仪器测试（需要已连接的模拟器或设备）：

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

本文档只说明项目支持的构建入口，不代表在当前环境或当前提交上已经重新执行成功。

## 当前发布状态

- 源码已经公开在 GitHub；
- 公开分支为 `main`；
- 当前没有正式签名的 Release APK 或 AAB；
- 当前没有 GitHub Release 资产；
- 当前没有应用内自动更新机制；
- 首次正式发布前仍需完成签名、版本递增、覆盖安装和数据迁移验证。

## 已知限制

- Android 本地应用，当前不提供账号、云同步或跨设备同步；
- Markdown、EPUB 的解析和展示存在当前实现的兼容边界；
- 未在全部 Android 版本和全部设备上验证，主要在 API 37 环境开发；
- 当前没有正式 Release 签名和稳定的分发通道；
- 第三方依赖和资源分别遵循其上游许可证，本项目许可证不能替代或覆盖第三方许可证；
- 本文档不构成法律意见。

## 许可证

本项目采用 **PolyForm Noncommercial License 1.0.0**：

- 官方文本：<https://polyformproject.org/licenses/noncommercial/1.0.0>
- 本仓库许可证文件：[LICENSE](LICENSE)

该许可证允许非商业目的下的使用、修改和分发，但限制商业用途。  
它不是传统 OSI 开源许可证，也不是法律意见；商业使用需要另行取得许可。

项目中使用的 AndroidX、Jetpack Compose、Kotlin、Gradle 等第三方组件，以及测试依赖，分别遵循其各自的许可证。  
分发本项目时仍需遵守这些第三方许可证；本项目许可证不替代第三方条款。

## 贡献与商业使用

当前项目没有设立正式的贡献政策，也未声明自动接受或拒绝外部贡献。  
如需提交代码、分发衍生作品或进行商业使用，请先与维护者沟通并取得相应许可。

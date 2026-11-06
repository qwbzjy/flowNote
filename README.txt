====================================================================
 flowNote — flomo 风格灵感速记 App
====================================================================

flowNote 是一款 Android 端的轻量级速记应用，主打"随手记下灵感"：
打开即写，回车即存，用 #标签 代替文件夹来组织内容。
纯本地应用，无需登录、无任何网络请求。

--------------------------------------------------------------------
 功能特性
--------------------------------------------------------------------
 - 速记卡片流：首页按时间倒序展示 memo，卡片流式布局
 - #标签：正文内以 #标签 形式行内书写，自动解析、高亮渲染，
   并写入标签库；点击标签即可筛选
 - 标签管理：查看全部标签及使用计数，支持重命名（联动所有 memo）
 - 全文搜索：按关键词检索 memo 内容与标签
 - 图片附件：每条 memo 可附多张图片（自动读取 EXIF 方向，缩略图网格展示）
 - 语音输入：点击麦克风按钮，调用系统语音识别转文字插入正文
 - 备份与恢复：一键导出 zip 备份包（JSON + 图片），支持导入恢复
 - 标签重命名时若目标名已存在，则自动合并标签

--------------------------------------------------------------------
 技术栈
--------------------------------------------------------------------
 - 语言：Kotlin 2.3，JDK 17
 - UI：Jetpack Compose + Material 3（Compose BOM 2026.02.00）
 - 导航：Navigation Compose，type-safe 路由（kotlinx.serialization）
 - 数据库：Room 2.8（KSP 编译，schema 导出到 app/schemas）
 - 图片加载：Coil 3
 - 备份序列化：kotlinx.serialization JSON + java.util.zip
 - 依赖注入：手写 AppContainer（未引入 Hilt/Koin）
 - 最低支持：minSdk 26（Android 8.0），targetSdk 35，compileSdk 36

--------------------------------------------------------------------
 目录结构
--------------------------------------------------------------------
 app/src/main/java/com/wenbo/flownote/
   MainActivity.kt / FlowNoteApplication.kt   入口与 Application
   di/AppContainer.kt                         手写依赖容器
   navigation/                                 type-safe 路由与 App 骨架
   core/                                       纯逻辑：TagParser、TimeFormatter
   domain/                                     领域模型与仓库接口
   data/                                       实现：Room 数据库、ImageStore、Backup
   feature/                                    各页面：home / edit / search / tags
   ui/                                         主题与通用组件（标签高亮、图片网格等）
   speech/                                     语音识别引擎封装

--------------------------------------------------------------------
 构建与运行
--------------------------------------------------------------------
 环境要求：JDK 17、Android Studio（AGP 8.13.2）。
 注意：需保证 JAVA_HOME 指向有效的 JDK 17，必要时先手动设置。

   ./gradlew assembleDebug        # 构建 Debug APK
   ./gradlew test                 # 运行单元测试（TagParser / TimeFormatter / Backup）

 构建产物：app/build/outputs/apk/debug/app-debug.apk

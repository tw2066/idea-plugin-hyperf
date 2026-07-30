# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

PhpStorm 插件，为 [Hyperf](https://www.hyperf.io) PHP 框架提供 IDE 支持：路由 Controller 补全/跳转、`config()` 配置键索引/补全/跳转、`trans()` 翻译键索引/补全/跳转。目标平台为 **PhpStorm 2026.2（PS-262）**，`since-build = 262`，仅支持 2026.x，不向后兼容旧 IDE。

## 构建与运行

必须使用 **JDK 25**（2026.2 平台类以 Java 25 编译，用更低版本会报 `类文件具有错误的版本 69.0`）。本机 JDK 25 路径：`D:\Program Files\PhpWebStudy-Data\app\openjdk-25.0.4`。

所有 Gradle 命令都需通过 `-Dorg.gradle.java.home` 指定 JDK 25 作为 Gradle JVM：

```bash
# 编译
./gradlew --no-daemon "-Dorg.gradle.java.home=D:\Program Files\PhpWebStudy-Data\app\openjdk-25.0.4" compileJava

# 打包插件（产物在 build/distributions/hyperf-<version>.zip）
./gradlew --no-daemon "-Dorg.gradle.java.home=D:\Program Files\PhpWebStudy-Data\app\openjdk-25.0.4" buildPlugin

# 起沙箱 IDE 实例调试（会启动一个带插件的 PhpStorm）
./gradlew --no-daemon "-Dorg.gradle.java.home=D:\Program Files\PhpWebStudy-Data\app\openjdk-25.0.4" runIde
```

- 首次构建需下载 PhpStorm 2026.2 SDK（数 GB，耗时较长），之后走 Gradle 缓存。
- 控制台输出为 GBK，乱码时管道加 `iconv -f GBK -t UTF-8`。
- **测试已禁用**：`src/test` 依赖已删除的 `LightCodeInsightFixtureTestCase`，在 build.gradle 中通过 `sourceSets.test` exclude 排除编译，未迁移到新测试框架。

## 构建系统的关键约束（勿随意改动）

- **平台依赖必须写成 `phpstorm(ideaVersion)` 括号调用**。`phpstorm ideaVersion` 空格语法会被 Groovy 解析成两条独立语句，导致平台本体依赖静默丢失，所有 IntelliJ 类（含 `PersistentStateComponent` 等核心类）编译报错。
- **`instrumentCode` 任务已禁用**：本地 JDK 25 不是 JetBrains Runtime，缺少 JBR 特有的 `Packages` 目录，插桩会报 `... openjdk-25.0.4\Packages does not exist`。插桩仅提供 `@NotNull` 运行时断言，禁用不影响功能。
- 依赖仓库走阿里云镜像 + `intellijPlatform.defaultRepositories()`。

## 代码架构

### 包结构

- `com.naixiaoxin.idea.hyperf.*` — 插件本体
- `fr.adrienbrault.idea.symfony2plugin.*` — 从 Symfony 插件移植的**通用 goto-completion 框架**，与 Hyperf 无关，不要在此引入 Hyperf 专属逻辑

### 核心机制：GotoCompletion 框架（symfony2plugin 包）

这是整个插件的骨架，理解它才能理解功能如何挂载：

1. **`CompletionContributor`**（注册为 `completion.contributor` EP，`language="any"`）和 **`GotoHandler`**（`gotoDeclarationHandler` EP）是两个入口，分别负责补全和跳转。
2. 它们通过 **`GotoCompletionUtil`** 收集所有 **`GotoCompletionContributor`** 实例（[`ConfigReferences`](src/main/java/com/naixiaoxin/idea/hyperf/config/ConfigReferences.java)、[`TranslationReferences`](src/main/java/com/naixiaoxin/idea/hyperf/translation/TranslationReferences.java)、[`ControllerReferences`](src/main/java/com/naixiaoxin/idea/hyperf/controller/ControllerReferences.java)），按语言过滤后调用其 `register(GotoCompletionRegistrarParameter)`。
3. 每个 References 类在 `register` 中用 **PSI 模式匹配**（`MethodMatcher.getMatchedSignatureWithDepth` 匹配 `\Hyperf\Contract\*Interface` 方法调用，`PhpElementsUtil.isFunctionReference` 匹配 `config()`/`trans()` 函数）判断当前位置是否命中，命中则返回一个 **`GotoCompletionProvider`**。
4. `GotoCompletionProvider` 的两个方法分工：**`getLookupElements()`** 提供补全项（从索引读 key 列表），**`getPsiTargets()`** 提供跳转目标（从索引找文件，再用 PSI visitor 定位具体 key 元素）。

### 功能模块

- **启用开关**：[HyperfStartupActivity](src/main/java/com/naixiaoxin/idea/hyperf/HyperfStartupActivity.java)（`postStartupActivity` EP）在打开含 `vendor/hyperf` 的项目时弹启用提示；静态方法 `isEnabled(...)` 被所有 References 在匹配前调用，未启用则全部功能短路。
- **配置/翻译键索引**：[`ConfigKeyStubIndex`](src/main/java/com/naixiaoxin/idea/hyperf/stub/ConfigKeyStubIndex.java)、[`TranslationKeyStubIndex`](src/main/java/com/naixiaoxin/idea/hyperf/stub/TranslationKeyStubIndex.java) 是 `FileBasedIndexExtension`，索引 PHP 文件 return 数组的键（用 [`ArrayReturnPsiRecursiveVisitor`](src/main/java/com/naixiaoxin/idea/hyperf/util/ArrayReturnPsiRecursiveVisitor.java) 遍历）。键前缀规则：配置文件路径（`config/autoload/xx.php` → `xx.` 前缀）由 [`ConfigFileUtil.matchConfigFile`](src/main/java/com/naixiaoxin/idea/hyperf/config/ConfigFileUtil.java) 计算；翻译命名空间（`storage/languages/<lang>/xx.php` → `xx`）由 [`TranslationUtil.getNamespaceFromFilePath`](src/main/java/com/naixiaoxin/idea/hyperf/translation/TranslationUtil.java) 计算。
- **设置**：[HyperfSettings](src/main/java/com/naixiaoxin/idea/hyperf/HyperfSettings.java)（`projectService` + `PersistentStateComponent`，存 `hyperf-plugin.xml`）保存启用状态、翻译语言/路径；[HyperfProjectSettingsForm](src/main/java/com/naixiaoxin/idea/hyperf/ui/HyperfProjectSettingsForm.java) 是 `projectConfigurable` 设置页（Swing .form 文件，非 Kotlin UI DSL）。

### 已迁移的关键 API（2026.2 平台现状，修改时遵循）

- 用 `project.getService(X.class)`，不要用已删除的 `ServiceManager`。
- `IconLoader.getIcon(path, XyzClass.class)` 需带 class 参数。
- 字符串工具用平台 `com.intellij.openapi.util.text.StringUtil`（`isEmptyOrSpaces`/`trimStart`），**平台不再捆绑 commons-lang2 的 `StringUtils`**。
- 通知用 `NotificationGroupManager` + plugin.xml 中注册的 `notificationGroup`（id 为 `Hyperf Plugin`），不要用 `Notifications.Bus`。
- 索引用 `java.util.HashMap`，不要用 `gnu.trove.THashMap`（已移除）。
- 设置对话框用 `com.intellij.openapi.options.ShowSettingsUtil`（`com.intellij.ide.actions` 下的版本已删除）。
- `project.getBaseDir()` 可能返回 null，所有调用点需防护。

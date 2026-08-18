# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

PhpStorm 插件，为 [Hyperf](https://www.hyperf.io) PHP 框架提供 IDE 支持：路由 Controller 补全/跳转、`config()` 配置键索引/补全/跳转、`trans()` 翻译键索引/补全/跳转、`.env` 环境变量补全/跳转、验证规则补全与悬停中文文档。目标平台为 **PhpStorm 2026.2（PS-262）**，`since-build = 262`，仅支持 2026.x，不向后兼容旧 IDE。

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
- `runIde -PopenProject="D:\java\hyperf-skeleton-3.2"` 可让沙箱启动后直接打开指定项目（build.gradle 中已加该参数支持）。hyperf-skeleton-3.2 的 `.idea/hyperf-plugin.xml` 已存 `pluginEnabled=true`，沙箱打开同一目录时设置直接生效，无需手动启用。

## 沙箱调试与验证

补全/跳转类功能无法无头验证，只能起沙箱实测；诊断信息全在日志里：

- **沙箱目录**：`.intellijPlatform/sandbox/hyperf/PS-2026.2/`（config/system/log/plugins）。**Gradle 控制台只透传 WARN+**，INFO 级（含插件加载记录、自己加的调试日志）要查 `.intellijPlatform/sandbox/hyperf/PS-2026.2/log/idea.log`。
- **启动验证**：idea.log 里确认 `Loaded custom plugins: Hyperf Base (<version>)` 和 `ProjectUtil - Opening existing project ...`；补全触发的异常也在这里（grep 包名 `com.base.idea.hyperf`）。
- **定位代码在哪一步短路**：CompletionContributor/GotoHandler 无法从 IDE 外驱动 GUI，只能在怀疑的路径上临时插 `Logger.getInstance(X.class).info("XXX-DEBUG ...")`，重启沙箱让用户复现，再 grep idea.log。用完即删。
- **中文 Windows 的 Ctrl+Space 坑**：输入法切换默认占用 Ctrl+Space，会导致"补全没反应"的假象（连 `__DIR__` 原生路径补全也像失效）。验证补全前先确认按键真的到了 IDE（改 IDE Keymap 的 Main menu → Code → Completion → Basic，或改用鼠标点菜单触发）。
- **内置 HTTP 服务**：沙箱带 `idea.is.internal=true`，内置 web 服务在 127.0.0.1 的 633xx 端口（PID 用进程命令行 `-Didea.system.path` 匹配找出，再 `netstat -ano | grep LISTENING` 看该 PID 监听）。`/api/about` 可用；`/api/file` 打开文件在 2026.2 返回 404，不能用于远程打开文件。
- 后台方式起 runIde 时，停掉该后台任务即关闭沙箱。

## 构建系统的关键约束（勿随意改动）

- **平台依赖必须写成 `phpstorm(ideaVersion)` 括号调用**。`phpstorm ideaVersion` 空格语法会被 Groovy 解析成两条独立语句，导致平台本体依赖静默丢失，所有 IntelliJ 类（含 `PersistentStateComponent` 等核心类）编译报错。
- **`instrumentCode` 任务已禁用**：本地 JDK 25 不是 JetBrains Runtime，缺少 JBR 特有的 `Packages` 目录，插桩会报 `... openjdk-25.0.4\Packages does not exist`。插桩仅提供 `@NotNull` 运行时断言，禁用不影响功能。
- 依赖仓库走阿里云镜像 + `intellijPlatform.defaultRepositories()`。

## 代码架构

### 包结构

- `com.base.idea.hyperf.*` — 插件本体
- `fr.adrienbrault.idea.symfony2plugin.*` — 从 Symfony 插件移植的**通用 goto-completion 框架**，与 Hyperf 无关，不要在此引入 Hyperf 专属逻辑

### 核心机制：GotoCompletion 框架（symfony2plugin 包）

这是整个插件的骨架，理解它才能理解功能如何挂载：

1. **`CompletionContributor`**（注册为 `completion.contributor` EP，`language="any"`）和 **`GotoHandler`**（`gotoDeclarationHandler` EP）是两个入口，分别负责补全和跳转。
2. 它们通过 **`GotoCompletionUtil`** 收集所有 **`GotoCompletionContributor`** 实例（[`ConfigReferences`](src/main/java/com/base/idea/hyperf/config/ConfigReferences.java)、[`TranslationReferences`](src/main/java/com/base/idea/hyperf/translation/TranslationReferences.java)、[`ControllerReferences`](src/main/java/com/base/idea/hyperf/controller/ControllerReferences.java)、`EnvReferences`、[`ValidationReferences`](src/main/java/com/base/idea/hyperf/validation/ValidationReferences.java)、[`BasePathReferences`](src/main/java/com/base/idea/hyperf/path/BasePathReferences.java)、[`ViewReferences`](src/main/java/com/base/idea/hyperf/view/ViewReferences.java)、[`AspectReferences`](src/main/java/com/base/idea/hyperf/aop/AspectReferences.java)、[`CacheListenerReferences`](src/main/java/com/base/idea/hyperf/cache/CacheListenerReferences.java)），按语言过滤后调用其 `register(GotoCompletionRegistrarParameter)`。
3. 每个 References 类在 `register` 中用 **PSI 模式匹配**（`MethodMatcher.getMatchedSignatureWithDepth` 匹配 `\Hyperf\Contract\*Interface` 方法调用，`PhpElementsUtil.isFunctionReference` 匹配 `config()`/`trans()` 函数）判断当前位置是否命中，命中则返回一个 **`GotoCompletionProvider`**。
4. `GotoCompletionProvider` 的两个方法分工：**`getLookupElements()`** 提供补全项（从索引读 key 列表），**`getPsiTargets()`** 提供跳转目标（从索引找文件，再用 PSI visitor 定位具体 key 元素）。

### 功能模块

- **启用开关**：[HyperfStartupActivity](src/main/java/com/base/idea/hyperf/HyperfStartupActivity.java)（`postStartupActivity` EP）在打开含 `vendor/hyperf` 的项目时弹启用提示；静态方法 `isEnabled(...)` 被所有 References 在匹配前调用，未启用则全部功能短路。
- **验证规则**（仅补全 + 悬停文档，不做跳转）：[`ValidationReferences`](src/main/java/com/base/idea/hyperf/validation/ValidationReferences.java) 内置与框架一致的静态规则表（`RULES`，4 列：规则名/参数提示/中文说明/是否补 `:`）。触发判定统一收敛在 **`isValidationRuleString(StringLiteralExpression)`**，四种场景：`FormRequest::rules()` 返回数组值、`ValidatorFactoryInterface::make()/validate()` 规则数组值（`MethodMatcher` 匹配第 2 参数 index=1）、`$scenes` 字符串键值、DTO 注解 `#[Validation(...)]` 的 `$rule`（`PhpAttribute.getFQN()` 比对 `\Hyperf\DTO\Annotation\Validation\Validation`）。补全前缀按 `|` 分隔取最后一段（`getLookupElements(CompletionContributorParameter)` 里 `withPrefixMatcher`）。悬停/Ctrl+Q 由 [`ValidationDocumentationProvider`](src/main/java/com/base/idea/hyperf/validation/ValidationDocumentationProvider.java)（`lang.documentationProvider` EP）提供——必须重写 `getCustomDocumentationElement` 返回字符串目标并缓存悬停偏移，`generateDoc` 才能按 `|` 切到当前规则（generateDoc 拿不到光标）。vendor 门控：装了 `hyperf/validation` 或 `hyperf/dto` 任一即启用。
- **BASE_PATH 路径补全/跳转**：[`BasePathReferences`](src/main/java/com/base/idea/hyperf/path/BasePathReferences.java) 匹配 `BASE_PATH . '/a/b' . $v` 拼接链中、与 `BASE_PATH` 常量之间只隔字符串字面量的字符串（`matchBasePathConcat` 递归求值左子树，PHP 拼接左结合），以项目根为基准补全子目录/文件（目录优先、选中目录补 `/` 并 `AutoPopupController` 重弹补全），Ctrl+B 跳到文件/目录。前缀/路径从 `getOriginalPosition()` 真实文本重算，规避补全副本 dummy 占位符污染。
- **配置/翻译键索引**：[`ConfigKeyStubIndex`](src/main/java/com/base/idea/hyperf/stub/ConfigKeyStubIndex.java)、[`TranslationKeyStubIndex`](src/main/java/com/base/idea/hyperf/stub/TranslationKeyStubIndex.java) 是 `FileBasedIndexExtension`，索引 PHP 文件 return 数组的键（用 [`ArrayReturnPsiRecursiveVisitor`](src/main/java/com/base/idea/hyperf/util/ArrayReturnPsiRecursiveVisitor.java) 遍历）。键前缀规则：配置文件路径（`config/autoload/xx.php` → `xx.` 前缀）由 [`ConfigFileUtil.matchConfigFile`](src/main/java/com/base/idea/hyperf/config/ConfigFileUtil.java) 计算；翻译命名空间（`storage/languages/<lang>/xx.php` → `xx`）由 [`TranslationUtil.getNamespaceFromFilePath`](src/main/java/com/base/idea/hyperf/translation/TranslationUtil.java) 计算。
- **视图模板补全/跳转**：[`ViewReferences`](src/main/java/com/base/idea/hyperf/view/ViewReferences.java) 匹配 `view()` 函数、`RenderInterface::render()/getContents()`、`FactoryInterface::make()` 第 1 参；[`ViewConfigUtil`](src/main/java/com/base/idea/hyperf/view/ViewConfigUtil.java) 解析 `config/autoload/view.php` 的 `config.view_path`（缺省 `storage/view`）与 `namespaces`。解析规则同 view-engine 的 `Finder`：点语法转 `/`，`pkg::name` 走命名空间目录（hint 由 `namespaces` 配置注册，未配置的运行时报 `No hint path defined`；另有隐藏约定：`view_path/vendor/<ns>` 存在即自动加入 hint 且排在配置路径之前），扩展名按 `blade.php/php/css/html` 顺序尝试。
- **AOP 切面字符串跳转**（仅跳转）：[`AspectReferences`](src/main/java/com/base/idea/hyperf/aop/AspectReferences.java) 匹配 `#[Aspect(...)]` 注解内字符串与 `AbstractAspect` 子类 `$classes/$annotations` 默认值，解析 `'FQN::method'`；方法部分支持 `*` 通配（列出全部匹配方法），类部分带 `*` 不跳转。
- **缓存监听器配对跳转**：[`CacheListenerStubIndex`](src/main/java/com/base/idea/hyperf/stub/CacheListenerStubIndex.java) 索引 `#[Cacheable/#[FailCache]` 的 `listener` 参数值与 `new DeleteListenerEvent('name', ...)` 第 1 参（上下文判定收敛在 [`CacheListenerUtil`](src/main/java/com/base/idea/hyperf/cache/CacheListenerUtil.java)，注解参数定位用 `ParameterList.getParameter("listener", 3)` 同时覆盖命名/位置参数）；[`CacheListenerReferences`](src/main/java/com/base/idea/hyperf/cache/CacheListenerReferences.java) 实现定向互跳（事件使用侧→注解声明侧，声明侧→全部使用点）与名字补全。
- **设置**：[HyperfSettings](src/main/java/com/base/idea/hyperf/HyperfSettings.java)（`projectService` + `PersistentStateComponent`，存 `hyperf-plugin.xml`）保存启用状态、翻译语言/路径；[HyperfProjectSettingsForm](src/main/java/com/base/idea/hyperf/ui/HyperfProjectSettingsForm.java) 是 `projectConfigurable` 设置页（Swing .form 文件，非 Kotlin UI DSL）。

### 已迁移的关键 API（2026.2 平台现状，修改时遵循）

- 用 `project.getService(X.class)`，不要用已删除的 `ServiceManager`。
- `IconLoader.getIcon(path, XyzClass.class)` 需带 class 参数。
- 字符串工具用平台 `com.intellij.openapi.util.text.StringUtil`（`isEmptyOrSpaces`/`trimStart`），**平台不再捆绑 commons-lang2 的 `StringUtils`**。
- 通知用 `NotificationGroupManager` + plugin.xml 中注册的 `notificationGroup`（id 为 `Hyperf Plugin`），不要用 `Notifications.Bus`。
- 索引用 `java.util.HashMap`，不要用 `gnu.trove.THashMap`（已移除）。
- 设置对话框用 `com.intellij.openapi.options.ShowSettingsUtil`（`com.intellij.ide.actions` 下的版本已删除）。
- `project.getBaseDir()` 可能返回 null，所有调用点需防护。

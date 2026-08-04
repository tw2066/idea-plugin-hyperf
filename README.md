# IntelliJ IDEA / PhpStorm Hyperf Plugin

为 [Hyperf](https://www.hyperf.io) PHP 框架提供 IDE 支持的 PhpStorm 插件，支持路由、配置、翻译、环境变量的补全与跳转。

目标平台为 **PhpStorm 2026.2（PS-262）**，`since-build = 262`，仅支持 2026.x。

## 功能

- **路由**（Controller 补全与跳转）：
  - `Router::get/post/put/patch/delete/options('/path', 'App\Controller\X@y')`
  - `Router::addRoute([...], '/path', 'App\Controller\X@y')`
  - 兼容 Hyperf 3.2 的 `@method static` 魔术方法 Router 写法
  - 补全列表来自 `\App\Controller` 命名空间下所有控制器的 public action
- **配置键**（索引、补全与跳转）：
  - 全局 `config('key')` 辅助函数
  - `\Hyperf\Contract\ConfigInterface::get()/has()` 方法调用
  - 索引 `config/` 与 `config/autoload/` 下 PHP 配置文件的 return 数组键
  - 多级键以 `.` 连接；支持 Hyperf 3.1+ 的子目录（`sub/a.php` → `sub.a`）与文件名含点号（`a.b.php` → `a.b`）
- **翻译键**（索引、补全与跳转）：
  - 全局 `trans()/__()` 辅助函数
  - `\Hyperf\Contract\TranslatorInterface::trans()` 方法调用
  - 索引 `storage/languages/<lang>/` 下翻译文件的键，跳转时优先当前语言目录
  - 仅在项目安装了 `hyperf/translation` 组件时启用
- **环境变量**（索引、补全与跳转）：
  - 全局 `env('KEY')` / `\Hyperf\Support\env('KEY')` 辅助函数
  - 索引项目根目录 `.env` / `.env.*` 文件（跳过注释与空行）

## 安装

1. 构建插件（见下文「构建」），或从 Releases 下载 zip。
2. PhpStorm：**Settings → Plugins → ⚙ → Install Plugin from Disk…** 选择 zip。
3. 打开 Hyperf 项目后，在 **Settings → Languages & Frameworks → PHP → Hyperf** 启用插件，并按需配置翻译语言与翻译目录。

## 构建

必须使用 **JDK 25**（2026.2 平台类以 Java 25 编译）。本机路径 `D:\Program Files\PhpWebStudy-Data\app\openjdk-25.0.4`：

```bash
# 编译
./gradlew --no-daemon "-Dorg.gradle.java.home=D:\Program Files\PhpWebStudy-Data\app\openjdk-25.0.4" compileJava

# 打包插件（产物在 build/distributions/hyperf-<version>.zip）
./gradlew --no-daemon "-Dorg.gradle.java.home=D:\Program Files\PhpWebStudy-Data\app\openjdk-25.0.4" buildPlugin

# 起沙箱 IDE 实例调试
./gradlew --no-daemon "-Dorg.gradle.java.home=D:\Program Files\PhpWebStudy-Data\app\openjdk-25.0.4" runIde
```

## 版本记录

- **1.0.6**：修复 `env()` 补全在字符串字面量内未生效的问题；`ConfigFileUtil` 支持文件名含点号的配置文件前缀。
- **1.0.5**：新增 `env()` 环境变量键的索引与补全跳转。
- **1.0.4**：支持 Hyperf 3.1+ 配置文件名包含点号（`a.b.php` → 前缀 `a.b`）。
- 早期版本：路由/配置/翻译基础功能，适配 Hyperf 2.x 老版本。

## 架构

- `com.naixiaoxin.idea.hyperf.*` — 插件本体
- `fr.adrienbrault.idea.symfony2plugin.*` — 从 Symfony 插件移植的通用 goto-completion 框架

所有功能通过 GotoCompletion 框架挂载：`CompletionContributor` 负责补全、`GotoHandler` 负责跳转，二者经 `GotoCompletionUtil` 收集各 References 实现（路由/配置/翻译/env），按语言过滤后调用。索引侧用 `FileBasedIndexExtension`（配置/翻译键为 PHP PSI 索引，env 键为纯文本内容索引）。

## 相关

- [Hyperf 官方文档](https://hyperf.wiki)
- 升级参考：`D:\java\hyperf-upgrade`（Hyperf 1.x → 3.1 升级指南）

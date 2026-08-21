# IntelliJ IDEA / PhpStorm Hyperf Plugin

为 [Hyperf](https://www.hyperf.io) PHP 框架提供 IDE 支持的 PhpStorm 插件，支持路由、配置、翻译、环境变量、验证规则、视图模板、AOP 切面、缓存监听器的补全与跳转，以及代码生成与常用命令的快捷菜单。

> **Fork 声明**：本项目 fork 自 [qiqizjl/idea-plugin-hyperf](https://github.com/qiqizjl/idea-plugin-hyperf)，原作者为 NaiXiaoXin（SeanWang）。本 fork 在原项目基础上进行了 Hyperf 3.x 适配（`@method` 魔术 Router、3.1+ 点号配置文件）、新增 `.env` 环境变量键补全跳转，并更名为 **hyperf base** 独立发布。原项目未声明开源许可证，本 fork 保留原作者署名与核心框架代码。

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
- **验证规则**（补全与悬停文档，不做跳转）：
  - `FormRequest::rules()` 返回数组的规则字符串值
  - `\Hyperf\Validation\Contract\ValidatorFactoryInterface::make()/validate()` 第 2 个参数（规则数组）的字符串值
  - `FormRequest::$scenes` 属性里字符串键对应的规则值
  - DTO 验证注解 `#[Validation('required|string')]` 的 `$rule` 参数（`\Hyperf\DTO\Annotation\Validation\Validation`）
  - 内置与框架一致的全套规则（`required`/`max:255`/`exists:table,column` 等）；补全项带中文注释、带参规则选中后自动补 `:`；鼠标悬停（或 Ctrl+Q）在某条规则上显示对应中文说明
  - 仅在项目安装了 `hyperf/validation` 或 `hyperf/dto` 组件时启用
- **BASE_PATH 路径**（补全与跳转）：
  - `BASE_PATH . '/a/b' . $v` 拼接链中的字符串，以项目根为基准补全子目录/文件（目录优先，选中目录自动补 `/` 并续弹下一级）
  - Ctrl+B 跳到对应文件/目录，效果等同 `__DIR__` 原生路径提示
- **视图模板**（补全与跳转）：
  - `view('user.list')` 辅助函数（hyperf/view-engine）
  - `\Hyperf\View\RenderInterface::render()/getContents()`、`\Hyperf\ViewEngine\Contract\FactoryInterface::make()` 方法调用
  - 按 view-engine 的 `Finder` 规则解析：点语法转目录，`pkg::name` 走 `view.php` 的 `namespaces` 配置（含 `view_path/vendor/<ns>` 自动 hint），扩展名按 `blade.php/php/css/html` 尝试
  - 视图根目录读取 `config/autoload/view.php` 的 `config.view_path`（缺省 `storage/view`）
- **AOP 切面**（跳转 + 方法名补全）：
  - `#[Aspect(classes: [...])]` 注解内与 `AbstractAspect` 子类 `$classes`/`$annotations` 属性默认值中的字符串
  - 支持 `'App\Service\Foo::bar'` 整串与 `Foo::class . '::bar'` 拼接两种写法
  - 类名已知时（`::` 前解析出 FQN 或拼接左侧 `::class`）输入方法名有补全提示；`'FQN::method'` 跳具体方法，方法部分支持 `*` 通配（列出全部匹配方法）；类部分带 `*` 不跳转
- **DI 接口绑定**（悬停文档，不抢占跳转）：
  - 悬停（或 Ctrl+Q）在接口上时，原生文档弹窗末尾追加 `Dependencies: \App\Foo\Impl`
  - 索引项目 `config/autoload/dependencies.php` 与 vendor 组件 `ConfigProvider` 的 `dependencies`，支持 `new PriorityDefinition(X::class, n)` 权重绑定
  - 生效规则与框架逐条对齐：项目 dependencies.php 无条件覆盖；vendor 间按 `composer.lock` 声明顺序模拟 `ProviderConfig::merge`（PriorityDefinition 形态有覆盖保护、权重高者赢、同权重 lock 靠前赢）
- **缓存监听器**（补全与双向跳转）：
  - `#[Cacheable(listener: "user-update")]` / `#[FailCache(listener: "...")]` 注解参数（命名/位置参数均支持）
  - `new DeleteListenerEvent("user-update", $args)` 构造第 1 参
  - 定向互跳：事件使用侧跳注解声明侧，声明侧列出全部使用点；两处均可补全已注册的监听器名
- **Crontab 回调**（补全与跳转）：
  - `#[Crontab(rule: "...", callback: "execute")]` 的 callback 字符串（命名/位置参数均支持）
  - 跳注解所在类的同名方法；补全列出类内方法名（与框架 `[当前类, callback]` 调用语义一致）
- **Crontab 规则**（悬停文档）：
  - 悬停（或 Ctrl+Q）在 `#[Crontab(rule: "...")]` 或 `->setRule('...')` 的规则字符串上，显示最近 5 次执行时间
  - 解析语义逐条对齐 `Hyperf\Crontab\Parser`：6 段带秒/5 段秒为 0、周日=0（0-6）、日与周 AND 关系、`*/n`、`a-b/n`、逗号列表
- **Hyperf 菜单**（代码生成与快捷命令，在内置 Terminal 执行）：
  - 主菜单栏新增顶级 **Hyperf** 菜单（仅插件启用时可见，可在设置中关闭）
  - `Code Generation`：`gen:controller/model/command/middleware/listener/job/process/aspect/request/resource/class/constant/migration/seeder`，弹输入框收类名后执行 `php bin/hyperf.php gen:*`（gen:model 表名可留空=全部表）
  - `Commands`：`describe:routes/listeners/aspects`、`vendor:publish`、`migrate / migrate:status / migrate:rollback`、`start`、`server:watch`、`crontab:run`、`queue:flush`、`gen:view-engine-cache`
  - PHP 路径解析顺序：设置页 `PHP Binary Path` → 项目 CLI 解释器 → PATH 中的 `php`；Unix 风格 PHP 路径（WSL）自动把脚本路径转为 `/mnt/...` 形式
- **命令行标记运行**（类名旁绿色运行按钮，等同 PHPUnit 测试图标体验）：
  - `Hyperf\Command\Command` 子类的类名左侧出现运行按钮，点击在内置 Terminal 执行 `php bin/hyperf.php <name>`
  - 命令名按框架生效优先级解析：`$signature` 首个 token → 构造函数 `parent::__construct('xx')` 首参 → `#[Command(name:)]` → `$name` 属性
  - 检测到参数（`$signature` 含 `{...}`、注解 `arguments/options` 数组、`getArguments()/getOptions()` 覆写、`configure()` 中 `addArgument/addOption`）时先弹输入框补齐参数，无参数则直接执行
- **XXL-JOB 标记运行**（hyperf/xxl-job-incubator，类名/方法名旁绿色运行按钮）：
  - 类形式：实现 `Hyperf\XxlJob\Handler\JobHandlerInterface`（如继承 `AbstractJobHandler`）且类上带 `#[XxlJob('name')]`，按钮挂在类名上
  - 方法形式：任意方法上带 `#[XxlJob('name')]`，按钮挂在方法名上；handler 名恒为注解 value 值（与框架注册逻辑一致，无类名/方法名兜底），value 为空不显示
  - 点击先弹 `--params` 输入框（留空=不带参数），在内置 Terminal 执行 `php bin/hyperf.php execute:xxl-job --handler=<name>`

## 安装

1. 构建插件（见下文「构建」），或从 Releases 下载 zip。
2. PhpStorm：**Settings → Plugins → ⚙ → Install Plugin from Disk…** 选择 zip。
3. 打开 Hyperf 项目后，在 **Settings → Languages & Frameworks → PHP → Hyperf Base** 启用插件，并按需配置翻译语言与翻译目录。

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

[CHANGELOG.md](CHANGELOG.md)

## 架构

- `com.base.idea.hyperf.*` — 插件本体
- `fr.adrienbrault.idea.symfony2plugin.*` — 从 Symfony 插件移植的通用 goto-completion 框架

所有功能通过 GotoCompletion 框架挂载：`CompletionContributor` 负责补全、`GotoHandler` 负责跳转，二者经 `GotoCompletionUtil` 收集各 References 实现（路由/配置/翻译/env/验证/视图/AOP/缓存监听器），按语言过滤后调用。索引侧用 `FileBasedIndexExtension`（配置/翻译键为 PHP PSI 索引，env 键为纯文本内容索引，缓存监听器名为上下文判定的 PSI 索引）。

## 相关

- [Hyperf 官方文档](https://hyperf.wiki)
- 升级参考：`D:\java\hyperf-upgrade`（Hyperf 1.x → 3.1 升级指南）

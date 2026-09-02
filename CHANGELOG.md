# Changelog

本插件所有版本的显著变更。格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)。
## [Unreleased]

### 新增

- API 路由(apidocs):解析 `runtime/container/http.json`(可在设置页配置路径),Search Everywhere 新增 "Routes" 标签页,按路由/方法名搜索并跳转到 `x-code-path` 指向的控制器方法
- API 路由标记:路由方法名旁显示 API 图标,点击生成 `POST http://host/path` 请求并追加到 Scratches 下 `<控制器名>.http`(HTTP Client 格式);一个方法对应多个路由时弹出列表选择

## [1.0.5]

### 优化

- 命令执行 Terminal 页签复用：菜单命令、命令行标记、XXL-JOB 标记执行时，已有 "Hyperf" 页签空闲则在其中执行（不再每次开新页签）；页签被关闭或 shell 忙（如 start 常驻进程）时开新页签

## [1.0.4]

### 新增

- XXL-JOB 标记运行：`#[XxlJob]` 注解的 Job 类（实现 JobHandlerInterface）/方法名旁运行按钮，点击弹窗输入 `--params` 后在 Terminal 执行 `execute:xxl-job --handler=<name>`（需安装 hyperf/xxl-job-incubator）

## [1.0.3]

### 新增

- 视图模板：`view()` / `RenderInterface::render()` / `getContents()` / `FactoryInterface::make()` 模板名补全与跳转（点语法 + `pkg::name` 命名空间，按 view.php 的 view_path / namespaces 解析）
- AOP 切面：`#[Aspect]` 注解与 `AbstractAspect` 的 `$classes` / `$annotations` 中 `'FQN::method'` 字符串跳转；支持 `Foo::class . '::method'` 拼接写法与方法名 `*` 通配；类名已知时方法名输入补全
- 缓存监听器：`#[Cacheable(listener: "...")]` / `#[FailCache]` 与 `new DeleteListenerEvent("...")` 监听器名的补全与定向互跳
- DI 接口绑定：悬停接口时文档弹窗末尾追加 `Dependencies: \FQN` 生效实现链接（可点击跳转）；覆盖项目 dependencies.php 与 vendor ConfigProvider 绑定，支持 `PriorityDefinition` 权重（生效规则与框架 ProviderConfig::merge / DefinitionSourceFactory 逐条对齐）
- Crontab：`#[Crontab(callback: "...")]` 回调方法名的补全与跳转
- Crontab：rule 表达式悬停显示最近 5 次执行时间
- Hyperf 顶级菜单：代码生成（devtool gen:*）与常用命令（describe:routes、migrate、start 等）在内置 Terminal 执行；PHP Binary Path 可配置（设置页 → 项目 CLI 解释器 → PATH 回退）
- 命令行标记：`Hyperf\Command\Command` 子类类名旁运行按钮，点击在 Terminal 执行（有参数先弹输入框）
- BASE_PATH 路径：`BASE_PATH . '/a/b'` 拼接链中字符串的子目录/文件补全与跳转
- 字符串字面量内输入字母时自动弹出补全（无建议不弹）

### 修复

- 项目在 WSL 文件系统时，菜单命令的 UNC 脚本路径转换
- 替换平台 internal API 调用（`ParameterList.getParameter(String, int)`），消除 Marketplace 验证告警

## [1.0.2]

### 修复

- 多份 vendor 副本（如 WSL 项目嵌套 vendor）时验证规则补全不生效

## [1.0.1]

### 新增

- 验证规则补全与悬停中文文档（FormRequest::rules()、ValidatorFactory::make()/validate() 规则数组、$scenes 值、DTO 注解 `#[Validation(...)]`）
- env() 补全在字符串字面量内未生效；支持文件名含点号的配置文件前缀
- env() 环境变量键的索引与补全跳转
- 支持 Hyperf 3.1+ 配置文件名包含点号（a.b.php → 前缀 a.b）

## [1.0.0]

### 新增

- 路由 Controller 补全与跳转（兼容 Hyperf 3.x @method 魔术 Router 写法）
- config() / ConfigInterface::get/has 配置键索引、补全与跳转
- trans() / __() 翻译键索引、补全与跳转

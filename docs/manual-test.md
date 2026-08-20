# 手动测试清单

补全/跳转/行标记类功能无法无头验证，统一在沙箱 IDE 中手动回归。每条用例都对应骨架项目
`D:\java\hyperf-skeleton-3.2` 里的真实夹具文件（代码注释中同步标注「测试点」）。

## 环境准备

```bash
./gradlew --no-daemon "-Dorg.gradle.java.home=D:\Program Files\PhpWebStudy-Data\app\openjdk-25.0.4" runIde -PopenProject="D:\java\hyperf-skeleton-3.2"
```

- 前置：项目在 **Settings → PHP → Hyperf Base** 已启用（骨架 .idea 已带配置）。
- 中文 Windows 下 Ctrl+Space 可能被输入法占用；补全用 **Code → Completion → Basic** 菜单或输入字母触发自动弹出验证。
- 诊断日志：`.intellijPlatform/sandbox/hyperf/PS-2026.2/log/idea.log`（不应有 `com.base.idea.hyperf` 相关异常）。

## 路由

夹具：`config/routes.php`

| # | 位置 | 操作 | 预期 |
|---|------|------|------|
| R1 | `/route-demo` 行的 `'App\Controller\IndexController@index'` | Ctrl+B | 跳 `IndexController::index` |
| R2 | 把回调字符串清空，引号内补全 | 补全 | 列出 `App\Controller\XxxController@方法` 列表 |

## 配置

夹具：`app/Controller/IndexController.php`（index 方法）

| # | 位置 | 操作 | 预期 |
|---|------|------|------|
| C1 | `$this->config->get('databases.default.charset')` | Ctrl+B | 跳 `config/autoload/databases.php` 对应键 |
| C2 | 把 get 参数清空，引号内补全 | 补全 | 列出全部配置键（含点号多级前缀） |
| C3 | `config('cache.default')`（可临时加到任意方法里） | Ctrl+B | 跳 `config/autoload/cache.php` 的 default 键 |

## 翻译

夹具：`storage/languages/zh_CN|en/messages.php` + `IndexController::transDemo`

| # | 位置 | 操作 | 预期 |
|---|------|------|------|
| T1 | `trans('messages.welcome')` | Ctrl+B | 跳 `zh_CN/messages.php` 的 welcome 键（优先 translationLang） |
| T2 | `trans('')` | 补全 | 列出 `messages.welcome` / `messages.nested.hello` |

## 环境变量

夹具：`.env` + `IndexController::index`

| # | 位置 | 操作 | 预期 |
|---|------|------|------|
| E1 | `env('DB_COLLATION')` | Ctrl+B | 跳 `.env` 的 DB_COLLATION 行 |
| E2 | `env('')` | 补全 | 列出 .env 全部键 |

## 验证规则

夹具：`app/Request/FooRequest.php`（rules/scenes）+ `IndexController::foo`（make 规则数组）

| # | 位置 | 操作 | 预期 |
|---|------|------|------|
| V1 | `FooRequest::rules()` 数组值里输入 `\|` 后 | 补全 | 规则名 + 中文说明；带参规则选中自动补 `:` |
| V2 | `required` / `starts_with:dd` 上 | 悬停 / Ctrl+Q | 该段规则的中文文档 |
| V3 | `foo()` 里 make 的规则数组值 | 补全/悬停 | 同 V1/V2 |
| V4 | `$scenes` 的 `'tar'` 场景规则值 | 补全/悬停 | 同 V1/V2 |

## BASE_PATH 路径

夹具：`base_path_demo.php`（项目根）

| # | 位置 | 操作 | 预期 |
|---|------|------|------|
| P1 | 用例 1 `BASE_PATH . ''` | 补全 | 列出项目根目录/文件（目录在前） |
| P2 | 用例 3 `'/config/autoload'` | Ctrl+B | 跳 config/autoload 目录 |
| P3 | 用例 4 多段拼接 `'/autoload/server.php'` | Ctrl+B | 跳 config/autoload/server.php |
| P4 | 用例 5 `$base . '/config'` | 补全 | 无任何提示（反例） |

## 视图模板

夹具：`app/Controller/ViewDemoController.php` + `storage/view/**` + `config/autoload/view.php`

| # | 位置 | 操作 | 预期 |
|---|------|------|------|
| W1 | `render('')` | 补全 | 列出 `index` / `user.list` / `admin::panel` |
| W2 | `render('user.list')` | Ctrl+B | 跳 `storage/view/user/list.blade.php` |
| W3 | `view('admin::panel')` | Ctrl+B | 跳 `storage/view/vendor/admin/panel.blade.php`（namespaces） |
| W4 | `getContents('index')` | Ctrl+B | 跳 `storage/view/index.blade.php` |

## AOP 切面

夹具：`app/Aspect/DemoAspect.php`（注解形式）+ `app/Aspect/FieldDemoAspect.php`（属性/拼接形式）

| # | 位置 | 操作 | 预期 |
|---|------|------|------|
| A1 | `'App\Controller\IndexController::index'` | Ctrl+B | 跳 IndexController::index |
| A2 | `'App\Controller\IndexController::in*'` | Ctrl+B | 列出该类全部 in* 方法 |
| A3 | `ViewDemoController::class . '::panel'` 的 `'::panel'` | Ctrl+B | 跳 ViewDemoController::panel |
| A4 | `::class . '::'` 或整串 `'FQN::'` 之后 | 输入字母 | 自动弹出该类方法名补全 |

## 缓存监听器

夹具：`app/Cache/DemoCacheService.php` + `IndexController::deleteUser`（同名事件）

| # | 位置 | 操作 | 预期 |
|---|------|------|------|
| L1 | 注解 `listener: "user-update"` | Ctrl+B | 列出全部 `new DeleteListenerEvent("user-update", ...)` 使用点（2 处） |
| L2 | 事件构造里的 `"user-update"` | Ctrl+B | 跳回注解的 listener 字符串 |
| L3 | `listener: ""` 空引号 | 补全 | 列出 `user-update` |

## DI 接口绑定（悬停文档，不抢占 Ctrl+B）

夹具：`app/Controller/DiDemoController.php` + `config/autoload/dependencies.php` + `app/ConfigProvider.php`

| # | 位置 | 操作 | 预期 |
|---|------|------|------|
| D1 | `UserServiceInterface`（类型/类名） | 悬停 / Ctrl+Q | 文档末尾 `Dependencies: \App\Service\UserService2`，链接可点击跳转 |
| D2 | `UserService`（实现类） | 悬停 | 无 Dependencies 追加，与原生一致 |
| D3 | `FactoryInterface`（vendor 绑定） | 悬停 | 追加 `Dependencies: \Hyperf\Validation\ValidatorFactoryFactory` |
| D4 | 任意类引用 | Ctrl+B | 原生行为（插件不插手） |

## Crontab

夹具：`app/Crontab/CallbackDemoCrontab.php`（callback）+ `app/Crontab/DemoCrontab.php`（rule 悬停）

| # | 位置 | 操作 | 预期 |
|---|------|------|------|
| K1 | `callback: "execute"` | Ctrl+B | 跳本类 `execute` 方法 |
| K2 | `callback: ""` 引号内 | 补全 | 列出本类方法名（execute / executeOther，不含魔术方法） |
| K3 | `#[Crontab(rule: "*/5 * * * *")]` 的 rule 上 | 悬停 | 显示该表达式的最近 5 次执行时间 |

## Hyperf 菜单与命令行标记

| # | 位置 | 操作 | 预期 |
|---|------|------|------|
| M1 | 主菜单栏 Hyperf → Code Generation | Controller 等 | 内置 Terminal 执行 `php bin/hyperf.php gen:*`（WSL 项目自动转 UNC 路径） |
| M2 | 主菜单栏 Hyperf → Commands | describe:routes / migrate / start 等 | Terminal 执行对应命令 |
| M3 | `Hyperf\Command\Command` 子类类名旁 | 点绿色运行图标 | Terminal 执行该命令；有参数先弹输入框 |

## 回归红线

- 补全在无建议场景（普通字符串、无关函数参数）不弹窗、不报错。
- Ctrl+B 在普通类/方法上保持原生行为（DI 只加悬停文档，不改跳转）。
- idea.log 无 `com.base.idea.hyperf` 相关异常。

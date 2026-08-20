# 手动测试清单

补全/跳转/行标记类功能无法无头验证，统一在沙箱 IDE 中手动回归。

## 环境准备

```bash
./gradlew --no-daemon "-Dorg.gradle.java.home=D:\Program Files\PhpWebStudy-Data\app\openjdk-25.0.4" runIde -PopenProject="D:\java\hyperf-skeleton-3.2"
```

- 骨架项目 `D:\java\hyperf-skeleton-3.2` 已内置各功能的测试夹具（代码注释中标注「测试点 N」）。
- 中文 Windows 下 Ctrl+Space 可能被输入法占用，补全建议用菜单 **Code → Completion → Basic** 或输入字母触发自动弹出验证。
- 补全/跳转前置：项目在 **Settings → PHP → Hyperf Base** 已启用（骨架项目的 .idea 已带配置）。

## 功能用例

### 路由（既有）

| # | 位置 | 操作 | 预期 |
|---|------|------|------|
| R1 | `Router::get('/x', '')` 引号内 | 补全 | 列出 `\App\Controller` 下控制器 `FQN@method` |
| R2 | `'App\Controller\IndexController@index'` | Ctrl+B | 跳到对应方法 |

### 配置 / 翻译 / env（既有）

| # | 位置 | 操作 | 预期 |
|---|------|------|------|
| C1 | `config('')` | 补全 | 列出配置键（含 3.1+ 点号文件名前缀） |
| C2 | `config('app.name')` | Ctrl+B | 跳到配置文件对应键 |
| T1 | `trans('')` | 补全 | 列出翻译键 |
| T2 | `trans('messages.xxx')` | Ctrl+B | 跳翻译文件（优先 translationLang 目录） |
| E1 | `env('')` | 补全 | 列出 .env 键 |
| E2 | `env('APP_NAME')` | Ctrl+B | 跳到 .env 对应行 |

### 验证规则（既有）

| # | 位置 | 操作 | 预期 |
|---|------|------|------|
| V1 | FormRequest `rules()` 数组值 | 补全 | 规则名 + 中文说明，带参规则选中自动补 `:` |
| V2 | 规则字符串某段上 | 悬停 / Ctrl+Q | 该段规则的中文文档 |

### BASE_PATH 路径（既有）

| # | 位置 | 操作 | 预期 |
|---|------|------|------|
| P1 | `BASE_PATH . '/storage/'` | 补全 | 目录优先列出子项，选中目录补 `/` 续弹 |
| P2 | `BASE_PATH . '/composer.json'` | Ctrl+B | 跳到文件 |

### 视图模板（1.0.3 新增）

夹具：[ViewDemoController.php](../../hyperf-skeleton-3.2/app/Controller/ViewDemoController.php) + `storage/view/**` + `config/autoload/view.php`

| # | 位置 | 操作 | 预期 |
|---|------|------|------|
| W1 | `render('')` | 补全 | 列出 `index` / `user.list` / `admin::panel` |
| W2 | `render('user.list')` | Ctrl+B | 跳 `storage/view/user/list.blade.php` |
| W3 | `view('admin::panel')` | Ctrl+B | 跳 `storage/view/vendor/admin/panel.blade.php`（namespaces 解析） |

### AOP 切面（1.0.3 新增）

夹具：[DemoAspect.php](../../hyperf-skeleton-3.2/app/Aspect/DemoAspect.php)（注解形式）+ [FieldDemoAspect.php](../../hyperf-skeleton-3.2/app/Aspect/FieldDemoAspect.php)（属性/拼接形式）

| # | 位置 | 操作 | 预期 |
|---|------|------|------|
| A1 | `'App\Controller\IndexController::index'` | Ctrl+B | 跳 IndexController::index |
| A2 | `'App\Controller\IndexController::in*'` | Ctrl+B | 列出该类全部 in* 方法 |
| A3 | `ViewDemoController::class . '::panel'` 的 `'::panel'` | Ctrl+B | 跳 ViewDemoController::panel |
| A4 | `::class . '::'` 或 整串 `'FQN::'` 之后 | 输入字母 | 自动弹出该类方法名补全 |

### 缓存监听器（1.0.3 新增）

夹具：[DemoCacheService.php](../../hyperf-skeleton-3.2/app/Cache/DemoCacheService.php)（另有 IndexController 里的同名事件）

| # | 位置 | 操作 | 预期 |
|---|------|------|------|
| L1 | 注解 `listener: "user-update"` | Ctrl+B | 跳到全部 `new DeleteListenerEvent("user-update", ...)` 使用点 |
| L2 | 事件构造里的 `"user-update"` | Ctrl+B | 跳回注解的 listener 字符串（唯一声明侧直接跳） |
| L3 | `listener: ""` 空引号 | 补全 | 列出已注册监听器名 `user-update` |

### DI 接口绑定（1.0.3 新增，悬停文档，不抢占 Ctrl+B）

夹具：[DiDemoController.php](../../hyperf-skeleton-3.2/app/Controller/DiDemoController.php) + [dependencies.php](../../hyperf-skeleton-3.2/config/autoload/dependencies.php) + [app/ConfigProvider.php](../../hyperf-skeleton-3.2/app/ConfigProvider.php)

| # | 位置 | 操作 | 预期 |
|---|------|------|------|
| D1 | `UserServiceInterface` 类型/类名上 | 悬停 / Ctrl+Q | 文档末尾 `Dependencies: \App\Service\UserService2`，链接可点击跳转 |
| D2 | `UserService`（实现类） | 悬停 | 无 Dependencies 追加，与原生一致 |
| D3 | `FactoryInterface`（vendor 绑定） | 悬停 | 追加 `Dependencies: \Hyperf\Validation\ValidatorFactoryFactory` |
| D4 | 任意类引用 | Ctrl+B | 原生行为（插件不插手） |

### Crontab（1.0.3 新增）

夹具：[CallbackDemoCrontab.php](../../hyperf-skeleton-3.2/app/Crontab/CallbackDemoCrontab.php)（callback）+ [DemoCrontab.php](../../hyperf-skeleton-3.2/app/Crontab/DemoCrontab.php)（rule 悬停）

| # | 位置 | 操作 | 预期 |
|---|------|------|------|
| K1 | `callback: "execute"` | Ctrl+B | 跳本类 execute 方法 |
| K2 | `callback: ""` 引号内 | 补全 | 列出本类方法名（execute / executeOther） |
| K3 | `#[Crontab(rule: "*/5 * * * *")]` 的 rule | 悬停 | 显示最近 5 次执行时间 |

### Hyperf 菜单与命令行标记（1.0.3 新增）

| # | 位置 | 操作 | 预期 |
|---|------|------|------|
| M1 | 主菜单栏 Hyperf | Code Generation → Controller 等 | 内置 Terminal 执行 `php bin/hyperf.php gen:*`（WSL 项目路径转 UNC） |
| M2 | 主菜单栏 Hyperf | Commands → describe:routes 等 | Terminal 执行对应命令 |
| M3 | `Hyperf\Command\Command` 子类类名旁 | 点绿色运行图标 | Terminal 执行该命令；有参数先弹输入框 |

## 回归红线

- 补全在无建议场景（普通字符串、无关函数参数）不弹窗、不报错。
- Ctrl+B 在普通类/方法上保持原生行为（DI 只加悬停文档，不改跳转）。
- idea.log 无 `com.base.idea.hyperf` 相关异常：
  `.intellijPlatform/sandbox/hyperf/PS-2026.2/log/idea.log`。

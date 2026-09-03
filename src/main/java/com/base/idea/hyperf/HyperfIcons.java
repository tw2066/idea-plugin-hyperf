package com.base.idea.hyperf;

import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

/**
 * 插件图标常量。
 *
 * <p>用于 Hyperf 补全列表、导航标记和相关工具窗口的图标。
 * 图标资源位于 resources/icons/ 目录下，统一采用 16x16 的 Spring 风格绿色线稿。
 */
public class HyperfIcons {

    /** 配置键补全图标 */
    public static final Icon CONFIG = IconLoader.getIcon("/icons/hyperf-config-mark.svg", HyperfIcons.class);
    /** 控制器路由补全图标 */
    public static final Icon CONTROLLER = IconLoader.getIcon("/icons/hyperf-controller-mark.svg", HyperfIcons.class);
    /** 翻译键补全图标 */
    public static final Icon TRANSLATION = IconLoader.getIcon("/icons/hyperf-translation-mark.svg", HyperfIcons.class);
    /** 验证器规则补全图标 */
    public static final Icon VALIDATION = IconLoader.getIcon("/icons/hyperf-validation-mark.svg", HyperfIcons.class);
    /** 视图模板补全图标 */
    public static final Icon VIEW = IconLoader.getIcon("/icons/hyperf-view-mark.svg", HyperfIcons.class);
    /** 缓存监听器名补全图标 */
    public static final Icon CACHE_LISTENER = IconLoader.getIcon("/icons/hyperf-cache-mark.svg", HyperfIcons.class);
    /** 环境变量补全图标 */
    public static final Icon ENV = IconLoader.getIcon("/icons/hyperf-env-mark.svg", HyperfIcons.class);
    /** 依赖注入绑定图标 */
    public static final Icon DI = IconLoader.getIcon("/icons/hyperf-di-mark.svg", HyperfIcons.class);
    /** AOP 切面类/方法图标 */
    public static final Icon AOP = IconLoader.getIcon("/icons/hyperf-aop-mark.svg", HyperfIcons.class);
    /** Crontab 回调方法图标 */
    public static final Icon CRONTAB = IconLoader.getIcon("/icons/hyperf-crontab-mark.svg", HyperfIcons.class);
    /** API 路由方法 gutter 图标 */
    public static final Icon API = IconLoader.getIcon("/icons/hyperf-route-mark.svg", HyperfIcons.class);
    /** Command / XXL-JOB 运行按钮图标 */
    public static final Icon RUN = IconLoader.getIcon("/icons/hyperf-run-mark.svg", HyperfIcons.class);

}

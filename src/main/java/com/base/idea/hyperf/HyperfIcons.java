package com.base.idea.hyperf;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

/**
 * 插件图标常量。
 *
 * <p>分别用于配置项、控制器路由、翻译键在补全列表中显示的图标。
 * 图标资源位于 resources/icons/ 目录下。
 */
public class HyperfIcons {

    /** 配置键补全图标 */
    public static final Icon CONFIG = IconLoader.getIcon("/icons/config.png", HyperfIcons.class);
    /** 控制器路由补全图标 */
    public static final Icon CONTROLLER = IconLoader.getIcon("/icons/controller.png", HyperfIcons.class);
    /** 翻译键补全图标 */
    public static final Icon TRANSLATION = IconLoader.getIcon("/icons/translation.png", HyperfIcons.class);
    /** 验证器规则补全图标 */
    public static final Icon VALIDATION = IconLoader.getIcon("/icons/validation.png", HyperfIcons.class);
    /** 视图模板补全图标 */
    public static final Icon VIEW = IconLoader.getIcon("/icons/icon.png", HyperfIcons.class);
    /** 缓存监听器名补全图标 */
    public static final Icon CACHE_LISTENER = IconLoader.getIcon("/icons/config.png", HyperfIcons.class);
    /** API 路由方法 gutter 图标(平台地球图标,同 IDEA Web 服务映射) */
    public static final Icon API = AllIcons.General.Web;

}

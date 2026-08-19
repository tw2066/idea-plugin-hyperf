package com.base.idea.hyperf.console;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.base.idea.hyperf.HyperfSettings;
import com.jetbrains.php.config.PhpProjectConfigurationFacade;
import com.jetbrains.php.config.interpreters.PhpInterpreter;
import org.jetbrains.annotations.NotNull;

/**
 * PHP 可执行文件路径解析。
 *
 * <p>解析顺序：Hyperf 设置页的 PHP Binary Path → PhpStorm 项目 CLI 解释器 → PATH 中的 php。
 * 最后一级直接返回 "php"，交给终端 shell 的 PATH 解析；
 * 执行失败时错误直接显示在终端里，无需在此探测。
 */
public class PhpBinaryResolver {

    @NotNull
    public static String resolve(@NotNull Project project) {
        // 设置页配置优先，直接使用不做文件存在性检查：
        // WSL/远程路径（如 /usr/bin/php8.4）在 Windows 文件系统下不可见，isFile 永远为 false
        String configured = HyperfSettings.getInstance(project).phpBinaryPath;
        if (!StringUtil.isEmptyOrSpaces(configured)) {
            return configured.trim();
        }

        PhpInterpreter interpreter = PhpProjectConfigurationFacade.getInstance(project).getInterpreter();
        if (interpreter != null) {
            String path = interpreter.getPathToPhpExecutable();
            if (!StringUtil.isEmptyOrSpaces(path)) {
                return path;
            }
        }

        return "php";
    }
}

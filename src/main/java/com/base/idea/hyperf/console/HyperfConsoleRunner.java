package com.base.idea.hyperf.console;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.terminal.ui.TerminalWidget;
import com.base.idea.hyperf.util.IdeHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.terminal.TerminalToolWindowManager;

/**
 * Hyperf 命令执行器：拼装 {@code php bin/hyperf.php <args>} 并在内置 Terminal 新 tab 中执行。
 *
 * <p>用 {@code createShellWidget(null, ...)} 拿到 {@link TerminalWidget} 接口 +
 * {@code sendCommandToExecute}，对新旧两套终端引擎（classic / reworked）都有效。
 */
public class HyperfConsoleRunner {

    /**
     * @param commandLine hyperf.php 之后的参数片段，如 {@code "gen:controller FooController"}
     */
    public static void runInTerminal(@NotNull Project project, @NotNull String commandLine) {
        VirtualFile baseDir = project.getBaseDir();
        if (baseDir == null) {
            return;
        }
        if (VfsUtil.findRelativeFile(baseDir, "bin", "hyperf.php") == null) {
            IdeHelper.notifyWarning(project, "bin/hyperf.php not found in project root — is this a Hyperf project?");
            return;
        }

        String phpPath = PhpBinaryResolver.resolve(project);
        String basePath = baseDir.getPath();
        String scriptPath = basePath + "/bin/hyperf.php";
        if (isUnixLike(phpPath)) {
            // PHP 解释器是 Unix 路径（如 WSL 的 /usr/bin/php），脚本路径也要转成 WSL 形式
            scriptPath = toUnixPath(scriptPath);
        }
        String fullCommand = quote(phpPath) + " " + quote(scriptPath) + " " + commandLine;

        ApplicationManager.getApplication().invokeLater(() -> {
            TerminalWidget widget = TerminalToolWindowManager.getInstance(project)
                    .createShellWidget(null, basePath, true, true);
            widget.sendCommandToExecute(fullCommand);
        });
    }

    /** 路径含空格时包双引号（cmd / PowerShell / bash 都认双引号） */
    private static String quote(@NotNull String path) {
        return path.contains(" ") ? "\"" + path + "\"" : path;
    }

    /** Unix 风格路径（/ 开头），用于判断 PHP 解释器运行在 WSL/Linux 环境 */
    private static boolean isUnixLike(@NotNull String path) {
        return path.startsWith("/");
    }

    /** Windows 路径转 WSL 路径：D:\a\b 或 D:/a/b → /mnt/d/a/b */
    private static String toUnixPath(@NotNull String windowsPath) {
        String path = windowsPath.replace('\\', '/');
        if (path.length() > 2 && Character.isLetter(path.charAt(0)) && path.charAt(1) == ':') {
            path = "/mnt/" + Character.toLowerCase(path.charAt(0)) + path.substring(2);
        }
        return path;
    }
}

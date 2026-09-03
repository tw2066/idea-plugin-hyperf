package com.base.idea.hyperf.console;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.content.Content;
import com.base.idea.hyperf.util.HyperfRootUtil;
import com.base.idea.hyperf.util.IdeHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.terminal.ShellTerminalWidget;
import org.jetbrains.plugins.terminal.TerminalToolWindowManager;

import java.io.IOException;

/**
 * Hyperf 命令执行器：拼装 {@code php bin/hyperf.php <args>} 并在内置 Terminal 执行。
 *
 * <p>用 createLocalShellWidget + executeCommand：233～262 都有的公共 API；
 * createShellWidget / TerminalWidget.sendCommandToExecute 在 233 不存在。
 *
 * <p>tab 复用策略：插件创建的 "Hyperf" tab 按项目记住，shell 空闲时在其上直接执行新命令
 * （避免每次执行都开新页签）；tab 被关闭或 shell 忙（如 start 常驻进程）时开新 tab。
 */
public class HyperfConsoleRunner {

    /** 按项目记住插件创建的 Hyperf tab，用于空闲时复用；tab 关闭后引用随新建替换 */
    private static final Key<ShellTerminalWidget> HYPERF_TAB_KEY = Key.create("hyperf.console.terminalWidget");

    /**
     * @param commandLine hyperf.php 之后的参数片段，如 {@code "gen:controller FooController"}
     */
    public static void runInTerminal(@NotNull Project project, @NotNull String commandLine) {
        // 命令锚定 Hyperf 应用根（支持应用在项目子目录的场景）
        VirtualFile rootDir = HyperfRootUtil.resolve(project);
        if (rootDir == null || VfsUtil.findRelativeFile(rootDir, "bin", "hyperf.php") == null) {
            IdeHelper.notifyWarning(project, "bin/hyperf.php not found — is this a Hyperf project?");
            return;
        }

        String phpPath = PhpBinaryResolver.resolve(project);
        String basePath = rootDir.getPath();
        String scriptPath = basePath + "/bin/hyperf.php";
        if (isUnixLike(phpPath)) {
            // PHP 解释器是 Unix 路径（如 WSL 的 /usr/bin/php），脚本路径也要转成 WSL 形式
            scriptPath = toUnixPath(scriptPath);
        }
        String fullCommand = quote(phpPath) + " " + quote(scriptPath) + " " + commandLine;

        ApplicationManager.getApplication().invokeLater(() -> {
            TerminalToolWindowManager manager = TerminalToolWindowManager.getInstance(project);
            ShellTerminalWidget widget = project.getUserData(HYPERF_TAB_KEY);
            if (widget == null || !isTabAlive(manager, widget)) {
                runInNewTab(project, manager, basePath, fullCommand);
                return;
            }
            executeWhenIdle(project, widget, basePath, fullCommand);
        });
    }

    /**
     * 忙闲判断放池线程：hasRunningCommands 在 262 断言禁止 EDT（winp 进程枚举），
     * 判断完再回到 EDT 复用或开新 tab。
     */
    private static void executeWhenIdle(@NotNull Project project, @NotNull ShellTerminalWidget widget,
                                        @NotNull String basePath, @NotNull String fullCommand) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            boolean busy = isBusy(widget);
            ApplicationManager.getApplication().invokeLater(() -> {
                TerminalToolWindowManager manager = TerminalToolWindowManager.getInstance(project);
                if (busy || !isTabAlive(manager, widget)) {
                    runInNewTab(project, manager, basePath, fullCommand);
                    return;
                }
                activateTab(manager, widget);
                executeCommand(project, widget, fullCommand);
            }, project.getDisposed());
        });
    }

    /** 无法判断忙闲（非本地进程连接）时按空闲处理，行为等同用户手动粘贴命令 */
    private static boolean isBusy(@NotNull ShellTerminalWidget widget) {
        try {
            return widget.hasRunningCommands();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    /**
     * widget 对应的 tab 是否仍在 Terminal 工具窗中。
     * getWidgets() 是 internal API，改用遍历 content 反查（与 activateTab 同一路径）。
     */
    private static boolean isTabAlive(@NotNull TerminalToolWindowManager manager, @NotNull ShellTerminalWidget widget) {
        ToolWindow toolWindow = manager.getToolWindow();
        if (toolWindow == null) {
            return false;
        }
        for (Content content : toolWindow.getContentManager().getContents()) {
            if (TerminalToolWindowManager.getWidgetByContent(content) == widget) {
                return true;
            }
        }
        return false;
    }

    private static void runInNewTab(@NotNull Project project, @NotNull TerminalToolWindowManager manager,
                                    @NotNull String basePath, @NotNull String fullCommand) {
        ShellTerminalWidget widget = manager.createLocalShellWidget(basePath, "Hyperf", true, true);
        project.putUserData(HYPERF_TAB_KEY, widget);
        executeCommand(project, widget, fullCommand);
    }

    private static void executeCommand(@NotNull Project project, @NotNull ShellTerminalWidget widget,
                                       @NotNull String fullCommand) {
        try {
            widget.executeCommand(fullCommand);
        } catch (IOException e) {
            IdeHelper.notifyWarning(project, "Terminal 命令执行失败: " + e.getMessage());
        }
    }

    /** 选中复用的 tab 并激活 Terminal 工具窗，焦点行为与新建 tab（requestFocus=true）一致 */
    private static void activateTab(@NotNull TerminalToolWindowManager manager, @NotNull ShellTerminalWidget widget) {
        ToolWindow toolWindow = manager.getToolWindow();
        if (toolWindow == null) {
            return;
        }
        for (Content content : toolWindow.getContentManager().getContents()) {
            if (TerminalToolWindowManager.getWidgetByContent(content) == widget) {
                toolWindow.getContentManager().setSelectedContent(content);
                break;
            }
        }
        toolWindow.activate(null);
    }

    /** 路径含空格时包双引号（cmd / PowerShell / bash 都认双引号）；包级可见供单测 */
    static String quote(@NotNull String path) {
        return path.contains(" ") ? "\"" + path + "\"" : path;
    }

    /** Unix 风格路径（/ 开头），用于判断 PHP 解释器运行在 WSL/Linux 环境；包级可见供单测 */
    static boolean isUnixLike(@NotNull String path) {
        return path.startsWith("/");
    }

    /**
     * Windows 路径转 WSL 路径（包级可见供单测）：
     * D:\a\b 或 D:/a/b → /mnt/d/a/b；
     * 项目位于 WSL 文件系统时的 UNC 形态 //wsl.localhost/<发行版>/home/... 或 //wsl$/<发行版>/...
     * → 去掉 UNC 前缀得到 WSL 内部路径 /home/...
     */
    static String toUnixPath(@NotNull String windowsPath) {
        String path = windowsPath.replace('\\', '/');
        for (String prefix : new String[]{"//wsl.localhost/", "//wsl$/"}) {
            if (path.startsWith(prefix)) {
                int rest = path.indexOf('/', prefix.length());
                return rest >= 0 ? path.substring(rest) : "/";
            }
        }
        if (path.length() > 2 && Character.isLetter(path.charAt(0)) && path.charAt(1) == ':') {
            path = "/mnt/" + Character.toLowerCase(path.charAt(0)) + path.substring(2);
        }
        return path;
    }
}

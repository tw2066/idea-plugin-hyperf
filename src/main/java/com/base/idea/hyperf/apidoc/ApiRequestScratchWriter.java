package com.base.idea.hyperf.apidoc;

import com.base.idea.hyperf.util.IdeHelper;
import com.intellij.ide.actions.OpenInRightSplitAction;
import com.intellij.ide.scratch.ScratchFileService;
import com.intellij.ide.scratch.ScratchRootType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * 把 API 路由生成 HTTP Client 请求行,追加到 Scratches 下 <控制器短名>.http 并打开。
 *
 * <p>格式与 HTTP Client 约定一致:请求行 + 空行,条目间 ### 分隔。
 * .http 文件类型由 PhpStorm 自带 HTTP Client 按扩展名识别,本插件不引入其编译期依赖。
 */
public final class ApiRequestScratchWriter {

    public static void appendAndOpen(@NotNull Project project, @NotNull ApiRoute route, @NotNull String baseUrl) {
        ApplicationManager.getApplication().invokeLater(() -> {
            String classFqn = route.getClassFqn();
            String fileName = classFqn.substring(classFqn.lastIndexOf('\\') + 1) + ".http";
            VirtualFile file;
            try {
                file = ScratchFileService.getInstance().findFile(
                        ScratchRootType.getInstance(), fileName, ScratchFileService.Option.create_if_missing);
            } catch (IOException e) {
                IdeHelper.notifyWarning(project, "Cannot create scratch file " + fileName + ": " + e.getMessage());
                return;
            }
            int offset;
            try {
                offset = ApplicationManager.getApplication().runWriteAction(
                        (com.intellij.openapi.util.ThrowableComputable<Integer, IOException>)
                                () -> appendRequestLine(file, route.getRequestLine(baseUrl)));
            } catch (IOException e) {
                IdeHelper.notifyWarning(project, "Cannot write " + fileName + ": " + e.getMessage());
                return;
            }
            // 右侧拆分打开并把光标定位到新请求行;已有右拆分且文件已在其中打开则复用,拆分失败回退普通打开
            OpenFileDescriptor descriptor = new OpenFileDescriptor(project, file, offset);
            if (isOpenInRightSplit(project, file)) {
                descriptor.navigate(true);
                return;
            }
            var window = OpenInRightSplitAction.Companion.openInRightSplit(project, file, descriptor, true);
            if (window == null) {
                descriptor.navigate(true);
            }
        });
    }

    /** 文件是否已在右侧拆分窗格中打开(与当前窗格不同组) */
    private static boolean isOpenInRightSplit(@NotNull Project project, @NotNull VirtualFile file) {
        FileEditorManagerEx fem = (FileEditorManagerEx) FileEditorManager.getInstance(project);
        EditorWindow current = fem.getCurrentWindow();
        for (EditorWindow window : fem.getWindows()) {
            if (window != current && window.isFileOpen(file)) {
                return true;
            }
        }
        return false;
    }

    /** 追加请求行并返回其起始偏移;已有内容非空且不以 ### 结尾时补分隔 */
    private static int appendRequestLine(@NotNull VirtualFile file, @NotNull String requestLine) throws IOException {
        String old = VfsUtilCore.loadText(file);
        StringBuilder sb = new StringBuilder(old);
        if (!old.isEmpty()) {
            if (!old.endsWith("\n")) {
                sb.append("\n");
            }
            if (!old.stripTrailing().endsWith("###")) {
                sb.append("\n###\n");
            }
            sb.append("\n");
        }
        int offset = sb.length();
        sb.append(requestLine).append("\n");
        VfsUtil.saveText(file, sb.toString());
        return offset;
    }
}

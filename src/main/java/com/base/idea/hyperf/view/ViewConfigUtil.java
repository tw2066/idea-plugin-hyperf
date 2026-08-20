package com.base.idea.hyperf.view;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.php.lang.psi.PhpFile;
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression;
import com.jetbrains.php.lang.psi.elements.ArrayHashElement;
import com.jetbrains.php.lang.psi.elements.ConcatenationExpression;
import com.jetbrains.php.lang.psi.elements.ConstantReference;
import com.jetbrains.php.lang.psi.elements.PhpReturn;
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 解析 {@code config/autoload/view.php}，得到视图根目录列表。
 *
 * <p>对应 view-engine 的 {@code Finder} 解析规则：普通视图名（点语法）在
 * {@code config.view_path} 下查找；{@code pkg::name} 在 {@code namespaces} 配置的
 * 命名空间目录下查找。配置缺省时回退到默认的 {@code storage/view}。
 */
public class ViewConfigUtil {

    /** 模板扩展名，顺序与 view-engine Finder 一致（blade.php 优先，保证双扩展名先被剥掉） */
    public static final String[] EXTENSIONS = {"blade.php", "php", "css", "html"};

    private static final String DEFAULT_VIEW_PATH = "/storage/view";

    /** 一个视图根目录：namespace 为 null 表示默认根（view_path），否则为 {@code ns::} 前缀 */
    public static class ViewRoot {
        @Nullable
        public final String namespace;
        @NotNull
        public final VirtualFile dir;

        ViewRoot(@Nullable String namespace, @NotNull VirtualFile dir) {
            this.namespace = namespace;
            this.dir = dir;
        }
    }

    /** 收集项目全部视图根目录（默认根 + namespaces 配置），目录不存在则跳过 */
    @NotNull
    public static List<ViewRoot> getViewRoots(@NotNull Project project) {
        List<ViewRoot> roots = new ArrayList<>();
        VirtualFile baseDir = project.getBaseDir();
        if (baseDir == null) {
            return roots;
        }

        String viewPath = DEFAULT_VIEW_PATH;
        Map<String, List<String>> namespaces = new LinkedHashMap<>();

        VirtualFile configFile = VfsUtil.findRelativeFile(baseDir, "config", "autoload", "view.php");
        if (configFile != null) {
            PsiFile psiFile = PsiManager.getInstance(project).findFile(configFile);
            if (psiFile instanceof PhpFile) {
                PhpReturn phpReturn = PsiTreeUtil.findChildOfType(psiFile, PhpReturn.class);
                PsiElement arrayExpr = phpReturn == null ? null : phpReturn.getFirstPsiChild();
                if (arrayExpr instanceof ArrayCreationExpression) {
                    String configuredPath = getNestedPath((ArrayCreationExpression) arrayExpr, "config", "view_path");
                    if (configuredPath != null) {
                        viewPath = configuredPath;
                    }
                    collectNamespaces((ArrayCreationExpression) arrayExpr, namespaces);
                }
            }
        }

        VirtualFile defaultDir = resolveUnderBase(baseDir, viewPath);
        if (defaultDir != null && defaultDir.isDirectory()) {
            roots.add(new ViewRoot(null, defaultDir));
        }
        for (Map.Entry<String, List<String>> entry : namespaces.entrySet()) {
            // 框架隐藏约定（FinderFactory::addNamespace）：命名空间注册后，
            // view_path/vendor/<ns> 若存在会自动加入 hint 且排在配置路径之前
            Set<String> seenDirs = new HashSet<>();
            if (defaultDir != null) {
                VirtualFile vendorDir = defaultDir.findFileByRelativePath("vendor/" + entry.getKey());
                if (vendorDir != null && vendorDir.isDirectory()) {
                    roots.add(new ViewRoot(entry.getKey(), vendorDir));
                    seenDirs.add(vendorDir.getPath());
                }
            }
            for (String path : entry.getValue()) {
                VirtualFile dir = resolveUnderBase(baseDir, path);
                if (dir != null && dir.isDirectory() && seenDirs.add(dir.getPath())) {
                    roots.add(new ViewRoot(entry.getKey(), dir));
                }
            }
        }
        return roots;
    }

    /** 取数组里 {@code outerKey => [...] => innerKey => 路径表达式} 的字符串值（支持 BASE_PATH 拼接） */
    @Nullable
    private static String getNestedPath(@NotNull ArrayCreationExpression root, @NotNull String outerKey, @NotNull String innerKey) {
        PsiElement outer = getHashValue(root, outerKey);
        if (!(outer instanceof ArrayCreationExpression)) {
            return null;
        }
        PsiElement inner = getHashValue((ArrayCreationExpression) outer, innerKey);
        return inner == null ? null : evalPath(inner);
    }

    /** 收集 {@code namespaces => ['admin' => BASE_PATH . '/...', ...]}，值可为字符串或字符串数组 */
    private static void collectNamespaces(@NotNull ArrayCreationExpression root, @NotNull Map<String, List<String>> namespaces) {
        PsiElement ns = getHashValue(root, "namespaces");
        if (!(ns instanceof ArrayCreationExpression)) {
            return;
        }
        for (ArrayHashElement hashElement : ((ArrayCreationExpression) ns).getHashElements()) {
            PsiElement key = hashElement.getKey();
            PsiElement value = hashElement.getValue();
            if (!(key instanceof StringLiteralExpression) || value == null) {
                continue;
            }
            String name = ((StringLiteralExpression) key).getContents();
            if (value instanceof ArrayCreationExpression) {
                for (ArrayHashElement item : ((ArrayCreationExpression) value).getHashElements()) {
                    PsiElement itemValue = item.getValue();
                    String path = itemValue == null ? null : evalPath(itemValue);
                    if (path != null) {
                        namespaces.computeIfAbsent(name, k -> new ArrayList<>()).add(path);
                    }
                }
                // 无键列表 ['path1', 'path2']
                for (PsiElement child : value.getChildren()) {
                    String path = evalPath(child);
                    if (path != null) {
                        namespaces.computeIfAbsent(name, k -> new ArrayList<>()).add(path);
                    }
                }
            } else {
                String path = evalPath(value);
                if (path != null) {
                    namespaces.computeIfAbsent(name, k -> new ArrayList<>()).add(path);
                }
            }
        }
    }

    @Nullable
    private static PsiElement getHashValue(@NotNull ArrayCreationExpression array, @NotNull String keyName) {
        for (ArrayHashElement hashElement : array.getHashElements()) {
            PsiElement key = hashElement.getKey();
            if (key instanceof StringLiteralExpression && keyName.equals(((StringLiteralExpression) key).getContents())) {
                return hashElement.getValue();
            }
        }
        return null;
    }

    /**
     * 求值路径表达式：字符串字面量直接取值，{@code BASE_PATH . '/a/b'} 拼接链递归求值
     * （BASE_PATH 视为项目根，对应空相对前缀）。
     */
    @Nullable
    private static String evalPath(@NotNull PsiElement expr) {
        if (expr instanceof StringLiteralExpression) {
            return ((StringLiteralExpression) expr).getContents();
        }
        if (expr instanceof ConstantReference && "BASE_PATH".equals(((ConstantReference) expr).getName())) {
            return "";
        }
        if (expr instanceof ConcatenationExpression) {
            PsiElement left = ((ConcatenationExpression) expr).getLeftOperand();
            PsiElement right = ((ConcatenationExpression) expr).getRightOperand();
            String leftValue = left == null ? null : evalPath(left);
            String rightValue = right == null ? null : evalPath(right);
            if (leftValue != null && rightValue != null) {
                return leftValue + rightValue;
            }
        }
        return null;
    }

    /** 把配置里的路径（绝对或 BASE_PATH 相对）解析为项目内目录；项目外路径返回 null */
    @Nullable
    private static VirtualFile resolveUnderBase(@NotNull VirtualFile baseDir, @NotNull String path) {
        String normalized = path.replace('\\', '/');
        String basePath = baseDir.getPath();
        if (normalized.startsWith(basePath)) {
            normalized = normalized.substring(basePath.length());
        }
        String[] segments = Arrays.stream(normalized.split("/")).filter(s -> !s.isEmpty()).toArray(String[]::new);
        return VfsUtil.findRelativeFile(baseDir, segments);
    }
}

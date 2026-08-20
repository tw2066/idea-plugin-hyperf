package com.base.idea.hyperf.path;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.Language;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiManager;
import com.jetbrains.php.lang.PhpLanguage;
import com.jetbrains.php.lang.psi.elements.ConcatenationExpression;
import com.jetbrains.php.lang.psi.elements.ConstantReference;
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression;
import com.base.idea.hyperf.HyperfStartupActivity;
import fr.adrienbrault.idea.symfony2plugin.codeInsight.GotoCompletionLanguageRegistrar;
import fr.adrienbrault.idea.symfony2plugin.codeInsight.GotoCompletionProvider;
import fr.adrienbrault.idea.symfony2plugin.codeInsight.GotoCompletionRegistrarParameter;
import fr.adrienbrault.idea.symfony2plugin.codeInsight.completion.CompletionContributorParameter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

/**
 * {@code BASE_PATH} 常量拼接路径的补全与跳转，效果等同 {@code __DIR__} 的原生路径提示。
 *
 * <p>Hyperf 骨架在入口文件 {@code define('BASE_PATH', dirname(__DIR__))}，即项目根目录；
 * PhpStorm 不会对 define 常量做路径补全，这里手动实现：
 * 匹配 {@code BASE_PATH . '/data-offline/load/' . $path} 这类拼接链中位于 BASE_PATH 之后、
 * 且与 BASE_PATH 之间只隔字符串字面量的字符串，以项目根目录为基准列出子目录/文件补全，
 * 并支持 Ctrl+B 跳到对应文件/目录。
 */
public class BasePathReferences implements GotoCompletionLanguageRegistrar {

    /** 目标常量名（Hyperf 约定的项目根目录常量） */
    private static final String BASE_PATH_CONSTANT = "BASE_PATH";

    @Override
    public boolean support(@NotNull Language language) {
        return PhpLanguage.INSTANCE == language;
    }

    @Override
    public void register(GotoCompletionRegistrarParameter registrar) {
        registrar.register(PlatformPatterns.psiElement().withParent(StringLiteralExpression.class), psiElement -> {
            if (!HyperfStartupActivity.isEnabled(psiElement)) {
                return null;
            }
            PsiElement parent = psiElement.getParent();
            if (parent instanceof StringLiteralExpression && matchBasePathConcat((StringLiteralExpression) parent) != null) {
                return new BasePathProvider(parent);
            }
            return null;
        });
    }

    /**
     * 若 literal 位于「以 BASE_PATH 开头、中间只隔字符串字面量」的拼接链上，
     * 返回 BASE_PATH 到 literal 之间已确定的路径段（如 {@code "/a/b"}）；否则返回 null。
     *
     * <p>PHP 拼接左结合：{@code BASE_PATH . '/a' . '/b' . $v} 解析为
     * concat(concat(concat(BASE_PATH,'/a'),'/b'),$v)，因此 literal 必须是某个 concat
     * 的右操作数，其左子树递归求值得到前缀。
     */
    @Nullable
    private static String matchBasePathConcat(@NotNull StringLiteralExpression literal) {
        PsiElement parent = literal.getParent();
        if (!(parent instanceof ConcatenationExpression) || ((ConcatenationExpression) parent).getRightOperand() != literal) {
            return null;
        }
        PsiElement left = ((ConcatenationExpression) parent).getLeftOperand();
        return left == null ? null : evalConcatLeft(left);
    }

    /** 左子树求值：BASE_PATH 常量视为 ""，字符串取其内容，拼接则递归；含变量等不可静态求值的部分返回 null */
    @Nullable
    private static String evalConcatLeft(@NotNull PsiElement expr) {
        if (expr instanceof ConstantReference && BASE_PATH_CONSTANT.equals(((ConstantReference) expr).getName())) {
            return "";
        }
        if (expr instanceof StringLiteralExpression) {
            return ((StringLiteralExpression) expr).getContents();
        }
        if (expr instanceof ConcatenationExpression) {
            PsiElement left = ((ConcatenationExpression) expr).getLeftOperand();
            PsiElement right = ((ConcatenationExpression) expr).getRightOperand();
            String leftValue = left == null ? null : evalConcatLeft(left);
            String rightValue = right instanceof StringLiteralExpression ? ((StringLiteralExpression) right).getContents() : null;
            if (leftValue != null && rightValue != null) {
                return leftValue + rightValue;
            }
        }
        return null;
    }

    /** 把 {@code "/a//b"} 这类相对路径解析到项目根下的 VirtualFile（空路径返回项目根本身），找不到返回 null */
    @Nullable
    private static VirtualFile resolveUnderBase(@NotNull VirtualFile baseDir, @NotNull String relativePath) {
        String[] segments = Arrays.stream(relativePath.split("/")).filter(s -> !s.isEmpty()).toArray(String[]::new);
        return VfsUtil.findRelativeFile(baseDir, segments);
    }

    /** BASE_PATH 路径的补全项与跳转目标提供者 */
    private static class BasePathProvider extends GotoCompletionProvider {

        BasePathProvider(PsiElement element) {
            super(element);
        }

        /** 无参版本拿不到 caret 位置，真实补全在带参版本完成；两者都会被框架调用，这里返回空避免重复 */
        @NotNull
        @Override
        public Collection<LookupElement> getLookupElements() {
            return Collections.emptyList();
        }

        /**
         * 列出「项目根 + caret 前已输入目录段」下的子项。
         *
         * <p>目录排在文件前（priority 1 > 0），选中目录自动补 "/" 并重新弹出补全。
         * 前缀与路径从真实文件文本（getOriginalPosition）重算，
         * 规避补全虚拟副本中 "IntellijIdeaRulezzz" 占位符的污染。
         */
        @Override
        public void getLookupElements(CompletionContributorParameter parameter) {
            PsiElement original = parameter.getCompletionParameters().getOriginalPosition();
            if (original == null || !(original.getParent() instanceof StringLiteralExpression)) {
                return;
            }
            StringLiteralExpression literal = (StringLiteralExpression) original.getParent();

            String basePrefix = matchBasePathConcat(literal);
            VirtualFile baseDir = getProject().getBaseDir();
            if (basePrefix == null || baseDir == null) {
                return;
            }

            // caret 在字符串内容内的偏移（原文件 offset - 字面量起始 - 前引号宽度）
            int caretInContent = parameter.getCompletionParameters().getOffset()
                    - literal.getTextRange().getStartOffset() - 1;
            String contents = literal.getContents();
            if (caretInContent < 0 || caretInContent > contents.length()) {
                return;
            }

            String typed = basePrefix + contents.substring(0, caretInContent);
            int lastSlash = typed.lastIndexOf('/');
            String dirPart = lastSlash < 0 ? "" : typed.substring(0, lastSlash);
            String prefix = lastSlash < 0 ? typed : typed.substring(lastSlash + 1);

            VirtualFile dir = resolveUnderBase(baseDir, dirPart);
            if (dir == null || !dir.isDirectory()) {
                return;
            }

            PsiManager psiManager = PsiManager.getInstance(getProject());
            CompletionResultSet resultSet = parameter.getCompletionResultSet().withPrefixMatcher(prefix);
            for (VirtualFile child : dir.getChildren()) {
                LookupElementBuilder builder = LookupElementBuilder.create(child.getName());
                PsiFileSystemItem psi = child.isDirectory()
                        ? psiManager.findDirectory(child)
                        : psiManager.findFile(child);
                Icon icon = psi == null ? null : psi.getIcon(0);
                if (icon != null) {
                    builder = builder.withIcon(icon);
                }
                LookupElement element = builder;
                if (child.isDirectory()) {
                    // 选中目录：补 "/" 并把 caret 移到其后，重新弹出下一级补全
                    element = PrioritizedLookupElement.withPriority(
                            builder.withInsertHandler((context, item) -> {
                                int tail = context.getTailOffset();
                                context.getDocument().insertString(tail, "/");
                                context.getEditor().getCaretModel().moveToOffset(tail + 1);
                                AutoPopupController.getInstance(context.getProject()).scheduleAutoPopup(context.getEditor());
                            }), 1.0);
                }
                resultSet.addElement(element);
            }
        }

        /** 解析拼接链的完整路径，跳到对应文件/目录 */
        @NotNull
        @Override
        public Collection<PsiElement> getPsiTargets(StringLiteralExpression element) {
            String basePrefix = matchBasePathConcat(element);
            VirtualFile baseDir = getProject().getBaseDir();
            if (basePrefix == null || baseDir == null) {
                return Collections.emptyList();
            }
            String fullPath = basePrefix + element.getContents();
            if (StringUtil.isEmptyOrSpaces(fullPath)) {
                return Collections.emptyList();
            }
            VirtualFile target = resolveUnderBase(baseDir, fullPath);
            if (target == null) {
                return Collections.emptyList();
            }
            PsiManager psiManager = PsiManager.getInstance(getProject());
            PsiFileSystemItem psi = target.isDirectory()
                    ? psiManager.findDirectory(target)
                    : psiManager.findFile(target);
            return psi == null ? Collections.emptyList() : Collections.singletonList(psi);
        }
    }
}

package com.base.idea.hyperf.validation;

import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.base.idea.hyperf.HyperfSettings;
import com.base.idea.hyperf.HyperfStartupActivity;
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 验证规则字符串的悬停/快速文档。
 *
 * <p>鼠标悬停（或 Ctrl+Q）在规则字符串上时，根据光标所在位置把
 * {@code required|max:255} 按 {@code |} 切开，定位到当前规则段，
 * 查 {@link ValidationReferences#RULES} 里的中文说明渲染出来。
 * 复用 {@link ValidationReferences#isValidationRuleString} 判断当前字符串是否为验证规则。
 */
public class ValidationDocumentationProvider extends AbstractDocumentationProvider {

    private static final Logger LOG = Logger.getInstance(ValidationDocumentationProvider.class);

    /** 上一次 {@link #getCustomDocumentationElement} 命中的字符串 */
    private static volatile StringLiteralExpression lastTarget;
    /** 对应的悬停偏移（在字符串内容内、相对 getContents()） */
    private static volatile int lastOffset = -1;

    /**
     * 悬停/快速文档的目标识别。字符串内容本身不是可解析引用，默认实现拿不到目标，
     * 必须在这里直接返回字符串元素，文档框架才会接着调 {@link #generateDoc}。
     * 同时把悬停偏移缓存下来，供 generateDoc 精确定位当前规则（generateDoc 拿不到光标）。
     */
    @Override
    public @Nullable PsiElement getCustomDocumentationElement(@NotNull Editor editor, @NotNull PsiFile file,
                                                              @Nullable PsiElement contextElement, int targetOffset) {
        LOG.warn("[ValDoc] getCustom contextElement=" + cls(contextElement) + " offset=" + targetOffset);
        lastTarget = null;
        lastOffset = -1;
        if (contextElement == null) {
            return null;
        }
        PsiElement parent = contextElement.getParent();
        if (parent instanceof StringLiteralExpression
                && ValidationReferences.isValidationRuleString((StringLiteralExpression) parent)) {
            StringLiteralExpression literal = (StringLiteralExpression) parent;
            lastTarget = literal;
            lastOffset = targetOffset - literal.getTextRange().getStartOffset() - 1;
            LOG.warn("[ValDoc] getCustom -> HIT '" + literal.getContents() + "' offsetInContent=" + lastOffset);
            return literal;
        }
        return null;
    }

    @Override
    public @Nullable String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
        LOG.warn("[ValDoc] generateDoc element=" + cls(element) + " original=" + cls(originalElement));
        // getCustomDocumentationElement 返回的是字符串本身，element 通常就是它
        PsiElement literalEl = element instanceof StringLiteralExpression ? element
                : (originalElement != null ? originalElement.getParent() : null);
        if (!(literalEl instanceof StringLiteralExpression)) {
            LOG.warn("[ValDoc] not a string literal: " + cls(literalEl));
            return null;
        }
        StringLiteralExpression literal = (StringLiteralExpression) literalEl;

        if (!HyperfStartupActivity.isEnabled(literal)) {
            LOG.warn("[ValDoc] plugin not enabled");
            return null;
        }
        if (!HyperfSettings.getInstance(literal.getProject()).validationEnabled) {
            LOG.warn("[ValDoc] validationEnabled=false");
            return null;
        }
        if (!ValidationReferences.isValidationRuleString(literal)) {
            LOG.warn("[ValDoc] isValidationRuleString=false: '" + literal.getContents() + "'");
            return null;
        }
        LOG.warn("[ValDoc] OK rule string: '" + literal.getContents() + "'");

        // 优先用 getCustomDocumentationElement 缓存的悬停偏移（同一字符串才有效）；
        // 否则退回 originalElement 位置；都取不到则用 0（首条规则）
        int caretInContent = (literal == lastTarget && lastOffset >= 0) ? lastOffset
                : (originalElement != null
                    ? originalElement.getTextRange().getStartOffset() - literal.getTextRange().getStartOffset() - 1
                    : 0);
        String contents = literal.getContents();
        if (caretInContent < 0) {
            caretInContent = 0;
        }
        if (caretInContent > contents.length()) {
            caretInContent = contents.length();
        }
        LOG.warn("[ValDoc] caretInContent=" + caretInContent + " -> segment");

        String ruleName = extractRuleAt(contents, caretInContent);
        if (ruleName == null) {
            return null;
        }
        String[] rule = findRule(ruleName);
        if (rule == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(DocumentationMarkup.DEFINITION_START);
        sb.append(escape(rule[0]));
        if (!rule[1].isEmpty() && !rule[0].contains(":")) {
            sb.append("<font color='#707070'>:").append(escape(rule[1])).append("</font>");
        }
        sb.append(DocumentationMarkup.DEFINITION_END);
        sb.append(DocumentationMarkup.CONTENT_START);
        sb.append(escape(rule[2]));
        sb.append(DocumentationMarkup.CONTENT_END);
        return sb.toString();
    }

    /**
     * 取出 contents 中 offset 所在、以 {@code |} 分隔的那一段规则名（去掉 {@code :} 后的参数）。
     * 命中空段（光标正好在 {@code |} 上或段内无字母）时返回 null。
     */
    private static @Nullable String extractRuleAt(@NotNull String contents, int offset) {
        int start = contents.lastIndexOf('|', Math.max(0, offset - 1)) + 1;
        int endIdx = contents.indexOf('|', offset);
        int end = endIdx < 0 ? contents.length() : endIdx;
        if (start >= end) {
            return null;
        }
        String segment = contents.substring(start, end);
        int colon = segment.indexOf(':');
        String name = colon < 0 ? segment : segment.substring(0, colon);
        name = name.trim();
        return name.isEmpty() ? null : name;
    }

    /** 在规则表里按名字精确匹配（含 {@code integer:strict} 这类带参独立项） */
    private static String @Nullable [] findRule(@NotNull String name) {
        for (String[] rule : ValidationReferences.RULES) {
            if (rule[0].equals(name)) {
                return rule;
            }
        }
        return null;
    }

    private static @NotNull String escape(@NotNull String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static @NotNull String cls(@Nullable PsiElement e) {
        return e == null ? "null" : e.getClass().getSimpleName() + "('" + abbrev(e.getText()) + "')";
    }

    private static @NotNull String abbrev(@NotNull String s) {
        s = s.replace("\n", " ");
        return s.length() > 30 ? s.substring(0, 30) + "…" : s;
    }
}

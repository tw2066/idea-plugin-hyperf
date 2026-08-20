package com.base.idea.hyperf.di;

import com.intellij.lang.LanguageDocumentation;
import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.jetbrains.php.PhpIndex;
import com.jetbrains.php.lang.PhpLanguage;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import com.base.idea.hyperf.HyperfStartupActivity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * DI 接口绑定的悬停文档追加。
 *
 * <p>鼠标悬停（或 Ctrl+Q）在接口上时，先链式调用 PHP 原生文档提供者拿到原生弹窗内容，
 * 再把生效实现（权重最高，见 {@link DiBindingResolver}）以
 * {@code Dependencies: \App\Foo\Impl} 追加到内容区末尾；实现类是可点击链接
 * （{@code psi_element://} 协议，经 {@link #getDocumentationElementForLink} 解析跳转）。
 * 非接口或无绑定时返回 null，完全交还原生文档。
 */
public class DiBindingDocumentationProvider extends AbstractDocumentationProvider {

    /** 平台文档弹窗只对 psi_element:// 协议的链接走 provider 解析（其他 scheme 会被当浏览器 URL） */
    private static final String LINK_PREFIX = "psi_element://";

    @Override
    public @Nullable String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
        if (!(element instanceof PhpClass) || !((PhpClass) element).isInterface()) {
            return null;
        }
        if (!HyperfStartupActivity.isEnabled(element)) {
            return null;
        }

        String fqn = StringUtil.trimStart(((PhpClass) element).getFQN(), "\\");
        DiBindingValue winner = DiBindingResolver.resolveWinningBinding(element.getProject(), fqn);
        if (winner == null) {
            return null;
        }

        // 标签与原生文档的 Signature/Source 一致用灰色；实现类显示完整命名空间
        String extra = "<br/>" + DocumentationMarkup.GRAYED_START + "Dependencies: " + DocumentationMarkup.GRAYED_END
                + "<a href=\"" + LINK_PREFIX + winner.implFqn + "\">\\" + winner.implFqn + "</a>";

        // 链式取原生文档（order="first" 使我们先于 PHP 原生提供者被调用，需自行委托）
        String baseDoc = null;
        for (DocumentationProvider provider : LanguageDocumentation.INSTANCE.allForLanguage(PhpLanguage.INSTANCE)) {
            if (provider instanceof DiBindingDocumentationProvider) {
                continue;
            }
            baseDoc = provider.generateDoc(element, originalElement);
            if (baseDoc != null) {
                break;
            }
        }
        if (baseDoc == null) {
            return extra;
        }
        // 插进内容区（CONTENT_END 之前），保持与原生文档一致的字体与颜色
        int contentEnd = baseDoc.lastIndexOf(DocumentationMarkup.CONTENT_END);
        if (contentEnd >= 0) {
            return baseDoc.substring(0, contentEnd) + extra + baseDoc.substring(contentEnd);
        }
        return baseDoc + extra;
    }

    /** 解析文档链接为对应类元素。注意：平台在调用前已剥掉 psi_element:// 前缀，这里收到的就是 FQN；
     * 解析不了（如原生文档里的方法/锚点链接）返回 null，交给原生提供者兜底 */
    @Override
    public @Nullable PsiElement getDocumentationElementForLink(PsiManager psiManager, String link, PsiElement context) {
        if (link == null || context == null || !HyperfStartupActivity.isEnabled(context)) {
            return null;
        }
        String fqn = StringUtil.trimStart(StringUtil.trimStart(link, LINK_PREFIX), "\\");
        for (PhpClass phpClass : PhpIndex.getInstance(context.getProject()).getAnyByFQN("\\" + fqn)) {
            return phpClass;
        }
        return null;
    }
}

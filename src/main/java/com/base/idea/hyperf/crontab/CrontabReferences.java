package com.base.idea.hyperf.crontab;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.Language;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.php.lang.PhpLanguage;
import com.jetbrains.php.lang.psi.elements.Method;
import com.jetbrains.php.lang.psi.elements.ParameterList;
import com.jetbrains.php.lang.psi.elements.PhpAttribute;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression;
import com.base.idea.hyperf.HyperfIcons;
import com.base.idea.hyperf.HyperfStartupActivity;
import fr.adrienbrault.idea.symfony2plugin.codeInsight.GotoCompletionLanguageRegistrar;
import fr.adrienbrault.idea.symfony2plugin.codeInsight.GotoCompletionProvider;
import fr.adrienbrault.idea.symfony2plugin.codeInsight.GotoCompletionRegistrarParameter;
import fr.adrienbrault.idea.symfony2plugin.codeInsight.utils.PhpElementsUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * {@code #[Crontab(callback: "execute")]} 回调方法名的补全与跳转。
 *
 * <p>callback 为字符串时框架按 {@code [当前类, callback]} 调用
 * （见 {@code Crontab::collectMethod}），故目标与补全均限定在注解所在的类内方法。
 * 注解可标在类或方法上，取最近的外层类。
 */
public class CrontabReferences implements GotoCompletionLanguageRegistrar {

    private static final String CRONTAB_ANNOTATION = "\\Hyperf\\Crontab\\Annotation\\Crontab";

    /** callback 在 Crontab 构造中的位置（rule,name,type,singleton,mutexPool,mutexExpires,callback） */
    private static final int CALLBACK_POSITION = 6;

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
            if (parent instanceof StringLiteralExpression
                    && isCrontabCallbackArgument((StringLiteralExpression) parent)) {
                return new CrontabCallbackProvider(parent);
            }
            return null;
        });
    }

    /** 字符串是否是 {@code #[Crontab(...)]} 的 callback 参数值（命名或第 7 个位置参数） */
    private static boolean isCrontabCallbackArgument(@NotNull StringLiteralExpression literal) {
        PhpAttribute attribute = PsiTreeUtil.getParentOfType(literal, PhpAttribute.class);
        if (attribute == null) {
            return false;
        }
        String fqn = attribute.getFQN();
        if (fqn == null || !fqn.equalsIgnoreCase(CRONTAB_ANNOTATION)) {
            return false;
        }
        ParameterList parameterList = attribute.getParameterList();
        if (parameterList == null) {
            return false;
        }
        PsiElement callbackParam = parameterList.getParameter("callback", CALLBACK_POSITION);
        return callbackParam != null && PsiTreeUtil.isAncestor(callbackParam, literal, false);
    }

    /** callback 方法名的补全项与跳转目标提供者（目标类内方法） */
    private static class CrontabCallbackProvider extends GotoCompletionProvider {

        CrontabCallbackProvider(PsiElement element) {
            super(element);
        }

        /** 列出注解所在类的全部方法名（排除魔术方法）作为补全项 */
        @NotNull
        @Override
        public Collection<LookupElement> getLookupElements() {
            Collection<LookupElement> lookupElements = new ArrayList<>();
            PhpClass phpClass = PsiTreeUtil.getParentOfType(getElement(), PhpClass.class);
            if (phpClass == null) {
                return lookupElements;
            }
            for (Method method : phpClass.getMethods()) {
                if (method.getName().startsWith("__")) {
                    continue;
                }
                lookupElements.add(LookupElementBuilder.create(method.getName()).withIcon(HyperfIcons.CONTROLLER));
            }
            return lookupElements;
        }

        /** 跳到注解所在类中同名的方法 */
        @NotNull
        @Override
        public Collection<PsiElement> getPsiTargets(StringLiteralExpression element) {
            String callback = element.getContents();
            PhpClass phpClass = PsiTreeUtil.getParentOfType(element, PhpClass.class);
            if (phpClass == null || StringUtil.isEmptyOrSpaces(callback)) {
                return Collections.emptyList();
            }
            Method method = PhpElementsUtil.getClassMethod(phpClass, callback);
            return method == null ? Collections.emptyList() : Collections.singletonList(method);
        }
    }
}

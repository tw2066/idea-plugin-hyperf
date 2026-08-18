package com.base.idea.hyperf.aop;

import com.intellij.lang.Language;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.php.lang.PhpLanguage;
import com.jetbrains.php.lang.psi.elements.Field;
import com.jetbrains.php.lang.psi.elements.Method;
import com.jetbrains.php.lang.psi.elements.PhpAttribute;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression;
import com.jetbrains.php.PhpIndex;
import com.base.idea.hyperf.HyperfStartupActivity;
import fr.adrienbrault.idea.symfony2plugin.Symfony2InterfacesUtil;
import fr.adrienbrault.idea.symfony2plugin.codeInsight.GotoCompletionLanguageRegistrar;
import fr.adrienbrault.idea.symfony2plugin.codeInsight.GotoCompletionProvider;
import fr.adrienbrault.idea.symfony2plugin.codeInsight.GotoCompletionRegistrarParameter;
import fr.adrienbrault.idea.symfony2plugin.codeInsight.utils.PhpElementsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * AOP 切面类/方法字符串的跳转（不做补全）。
 *
 * <p>{@code $classes} 里的规则形如 {@code 'App\Service\SomeClass'}、
 * {@code 'App\Service\SomeClass::someMethod'}、{@code 'App\Service\SomeClass::*Method'}。
 * 触发位置：
 * <ul>
 *   <li>{@code #[Aspect(classes: [...])]} 注解参数里的字符串；</li>
 *   <li>继承 {@code AbstractAspect} 的类的 {@code $classes}/{@code $annotations} 属性默认值数组里的字符串。</li>
 * </ul>
 * 跳转规则：类部分无通配符时可定位；方法部分精确匹配跳方法、带 {@code *} 通配列出所有
 * 匹配方法（无匹配时退化到类）；类部分带 {@code *} 无法静态枚举，不跳转。
 */
public class AspectReferences implements GotoCompletionLanguageRegistrar {

    private static final String ASPECT_ANNOTATION = "\\Hyperf\\Di\\Annotation\\Aspect";
    private static final String ABSTRACT_ASPECT = "\\Hyperf\\Di\\Aop\\AbstractAspect";

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
            if (!(parent instanceof StringLiteralExpression)) {
                return null;
            }
            StringLiteralExpression literal = (StringLiteralExpression) parent;
            if (isAspectAttributeArgument(literal) || isAspectFieldDefault(literal)) {
                return new AspectClassProvider(parent);
            }
            return null;
        });
    }

    /** 字符串是否位于 {@code #[Aspect(...)]} 注解内 */
    private static boolean isAspectAttributeArgument(@NotNull StringLiteralExpression literal) {
        PhpAttribute attribute = PsiTreeUtil.getParentOfType(literal, PhpAttribute.class);
        if (attribute == null) {
            return false;
        }
        String fqn = attribute.getFQN();
        return fqn != null && fqn.equalsIgnoreCase(ASPECT_ANNOTATION);
    }

    /** 字符串是否是 AbstractAspect 子类的 $classes/$annotations 属性默认值数组元素 */
    private static boolean isAspectFieldDefault(@NotNull StringLiteralExpression literal) {
        Field field = PsiTreeUtil.getParentOfType(literal, Field.class);
        if (field == null) {
            return false;
        }
        String fieldName = field.getName();
        if (!"classes".equals(fieldName) && !"annotations".equals(fieldName)) {
            return false;
        }
        PsiElement defaultValue = field.getDefaultValue();
        if (defaultValue == null || !PsiTreeUtil.isAncestor(defaultValue, literal, false)) {
            return false;
        }
        PhpClass phpClass = field.getContainingClass();
        return phpClass != null && new Symfony2InterfacesUtil().isInstanceOf(phpClass, ABSTRACT_ASPECT);
    }

    /** 切面类/方法字符串的跳转目标提供者（仅跳转） */
    private static class AspectClassProvider extends GotoCompletionProvider {

        AspectClassProvider(PsiElement element) {
            super(element);
        }

        /** 仅跳转，不提供补全项 */
        @NotNull
        @Override
        public Collection<com.intellij.codeInsight.lookup.LookupElement> getLookupElements() {
            return java.util.Collections.emptyList();
        }

        @NotNull
        @Override
        public Collection<PsiElement> getPsiTargets(StringLiteralExpression element) {
            Set<PsiElement> targets = new HashSet<>();
            String contents = element.getContents();

            String classPart = contents;
            String methodPart = null;
            if (contents.contains("::")) {
                String[] segments = contents.split("::", 2);
                classPart = segments[0];
                methodPart = segments.length > 1 ? segments[1] : null;
            }
            // 类部分带通配符无法静态枚举目标
            if (classPart.isEmpty() || classPart.contains("*")) {
                return targets;
            }

            for (PhpClass phpClass : PhpIndex.getInstance(getProject()).getAnyByFQN("\\" + classPart)) {
                if (methodPart == null || methodPart.isEmpty()) {
                    targets.add(phpClass);
                    continue;
                }
                if (methodPart.contains("*")) {
                    boolean matched = false;
                    Pattern pattern = wildcardToPattern(methodPart);
                    for (Method method : phpClass.getMethods()) {
                        if (pattern.matcher(method.getName()).matches()) {
                            targets.add(method);
                            matched = true;
                        }
                    }
                    if (!matched) {
                        targets.add(phpClass);
                    }
                    continue;
                }
                Method method = PhpElementsUtil.getClassMethod(phpClass, methodPart);
                targets.add(method != null ? method : phpClass);
            }
            return targets;
        }

        /** {@code *Method} → 全串匹配正则（* 转 .*, 其余逐段 quote） */
        @NotNull
        private static Pattern wildcardToPattern(@NotNull String wildcard) {
            StringBuilder regex = new StringBuilder();
            String[] segments = wildcard.split("\\*", -1);
            for (int i = 0; i < segments.length; i++) {
                if (i > 0) {
                    regex.append(".*");
                }
                if (!segments[i].isEmpty()) {
                    regex.append(Pattern.quote(segments[i]));
                }
            }
            return Pattern.compile(regex.toString());
        }
    }
}

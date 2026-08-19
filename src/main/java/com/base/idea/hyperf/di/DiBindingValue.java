package com.base.idea.hyperf.di;

import com.intellij.psi.PsiElement;
import com.jetbrains.php.lang.psi.elements.ClassConstantReference;
import com.jetbrains.php.lang.psi.elements.ClassReference;
import com.jetbrains.php.lang.psi.elements.NewExpression;
import com.jetbrains.php.lang.psi.elements.ParameterList;
import fr.adrienbrault.idea.symfony2plugin.codeInsight.utils.PhpElementsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 解析 DI 绑定值（{@code Impl::class} 或 {@code new PriorityDefinition(Impl::class, n)}）。
 *
 * <p>Hyperf 3.0.17+ 支持带权重的绑定。生效规则（ProviderConfig::merge +
 * DefinitionSourceFactory）不仅看权重，还看绑定形态：PriorityDefinition 形态
 * 对普通 ::class 形态有覆盖保护（哪怕权重 0），故这里同时记录形态标志。
 * 索引与跳转共用此解析。
 */
public class DiBindingValue {

    /** 实现类 FQN（无前导 \） */
    public final String implFqn;
    /** 绑定权重（普通 ::class 绑定为 0） */
    public final int priority;
    /** 是否 PriorityDefinition 形态（影响覆盖保护，独立于权重值） */
    public final boolean priorityDefinition;

    public DiBindingValue(@NotNull String implFqn, int priority, boolean priorityDefinition) {
        this.implFqn = implFqn;
        this.priority = priority;
        this.priorityDefinition = priorityDefinition;
    }

    private static final String PRIORITY_DEFINITION = "\\Hyperf\\Di\\Definition\\PriorityDefinition";

    /** 解析绑定值表达式；非 ::class / PriorityDefinition 形式（闭包工厂等）返回 null */
    @Nullable
    public static DiBindingValue parse(@Nullable PsiElement valueExpr) {
        if (valueExpr instanceof ClassConstantReference) {
            String fqn = PhpElementsUtil.getClassConstantFqn((ClassConstantReference) valueExpr);
            return fqn == null ? null : new DiBindingValue(fqn, 0, false);
        }
        if (!(valueExpr instanceof NewExpression)) {
            return null;
        }
        ClassReference classReference = ((NewExpression) valueExpr).getClassReference();
        if (classReference == null || classReference.getFQN() == null
                || !classReference.getFQN().equalsIgnoreCase(PRIORITY_DEFINITION)) {
            return null;
        }
        ParameterList parameterList = ((NewExpression) valueExpr).getParameterList();
        if (parameterList == null || !(parameterList.getParameter(0) instanceof ClassConstantReference)) {
            return null;
        }
        String fqn = PhpElementsUtil.getClassConstantFqn((ClassConstantReference) parameterList.getParameter(0));
        if (fqn == null) {
            return null;
        }
        return new DiBindingValue(fqn, parsePriority(parameterList.getParameter(1)), true);
    }

    /** 权重参数求值：整数字面量直接取值，表达式等无法静态求值时按 0 处理 */
    private static int parsePriority(@Nullable PsiElement priorityExpr) {
        if (priorityExpr == null) {
            return 0;
        }
        try {
            return Integer.parseInt(priorityExpr.getText().trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    /** 索引存储格式：{@code implFqn|priority|isPD} */
    @NotNull
    public String toIndexValue() {
        return implFqn + "|" + priority + "|" + (priorityDefinition ? 1 : 0);
    }

    /** 从索引值还原；格式非法返回 null */
    @Nullable
    public static DiBindingValue fromIndexValue(@NotNull String indexValue) {
        String[] segments = indexValue.split("\\|");
        if (segments.length != 3) {
            return null;
        }
        try {
            return new DiBindingValue(segments[0], Integer.parseInt(segments[1]), "1".equals(segments[2]));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

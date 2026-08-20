package com.base.idea.hyperf.cache;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.php.lang.psi.elements.ClassReference;
import com.jetbrains.php.lang.psi.elements.NewExpression;
import com.jetbrains.php.lang.psi.elements.ParameterList;
import com.jetbrains.php.lang.psi.elements.PhpAttribute;
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

/**
 * 缓存监听器名字的上下文判定（索引与跳转共用）。
 *
 * <p>Hyperf 缓存注解的 {@code listener} 参数（如 {@code #[Cacheable(listener: "user-update")]}）
 * 在 {@code CacheListenerCollector} 注册监听名；{@code new DeleteListenerEvent('user-update', $args)}
 * 按名字触发缓存删除。两侧通过同一个字符串配对，这里统一判定一个字符串字面量是否处于这两种上下文。
 */
public class CacheListenerUtil {

    /** 带 listener 参数的缓存注解 FQN（构造中 listener 都是第 4 个参数，index=3） */
    private static final List<String> LISTENER_ANNOTATIONS = Arrays.asList(
            "\\Hyperf\\Cache\\Annotation\\Cacheable",
            "\\Hyperf\\Cache\\Annotation\\FailCache"
    );

    /** 按监听名删缓存的事件类（构造第 1 个参数即监听名） */
    private static final String DELETE_LISTENER_EVENT = "\\Hyperf\\Cache\\Listener\\DeleteListenerEvent";

    /** 字符串是否是监听器名字：注解 listener 参数值，或 DeleteListenerEvent 构造第 1 参 */
    public static boolean isCacheListenerString(@NotNull StringLiteralExpression literal) {
        return isAnnotationListenerArgument(literal) || isDeleteListenerEventArgument(literal);
    }

    /** 声明侧：字面量位于 Cacheable/FailCache 注解内，且是 listener 参数（命名或第 4 个位置参数） */
    public static boolean isAnnotationListenerArgument(@NotNull StringLiteralExpression literal) {
        PhpAttribute attribute = PsiTreeUtil.getParentOfType(literal, PhpAttribute.class);
        if (attribute == null) {
            return false;
        }
        String fqn = attribute.getFQN();
        if (fqn == null || !LISTENER_ANNOTATIONS.contains(fqn)) {
            return false;
        }
        ParameterList parameterList = attribute.getParameterList();
        if (parameterList == null) {
            return false;
        }
        // getParameter(name, index)：命中命名参数 listener，无名参数时回退第 4 个位置参数
        PsiElement listenerParam = parameterList.getParameter("listener", 3);
        return listenerParam != null && PsiTreeUtil.isAncestor(listenerParam, literal, false);
    }

    /** 使用侧：字面量是 {@code new DeleteListenerEvent('name', ...)} 的第 1 个参数 */
    public static boolean isDeleteListenerEventArgument(@NotNull StringLiteralExpression literal) {
        PsiElement parent = literal.getParent();
        if (!(parent instanceof ParameterList)) {
            return false;
        }
        PsiElement owner = parent.getParent();
        if (!(owner instanceof NewExpression)) {
            return false;
        }
        ClassReference classReference = ((NewExpression) owner).getClassReference();
        if (classReference == null || classReference.getFQN() == null
                || !classReference.getFQN().equalsIgnoreCase(DELETE_LISTENER_EVENT)) {
            return false;
        }
        PsiElement firstParam = ((ParameterList) parent).getParameter(0);
        return firstParam == literal;
    }
}

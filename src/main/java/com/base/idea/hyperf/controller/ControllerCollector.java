package com.base.idea.hyperf.controller;

import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.openapi.project.Project;
import com.jetbrains.php.PhpIndex;
import com.jetbrains.php.lang.psi.elements.Method;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import com.base.idea.hyperf.util.PhpClassUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;

/**
 * 控制器收集器。
 *
 * <p>遍历 {@code \App\Controller} 命名空间（含子命名空间）下所有非抽象类，
 * 将每个 public、非静态、非魔术方法（非 __ 开头）视为一个可路由的 action，
 * 回调给访问者，供路由补全使用。
 *
 * @author NaiXiaoXin(SeanWang) <i@naixiaoxin.com>
 */
public class ControllerCollector {


    /**
     * 遍历所有控制器 action 并回调 visitor。
     */
    public static void visitControllerActions(@NotNull final Project project, @NotNull ControllerActionVisitor visitor) {
        // 只查询App\Controller中的路由
        Collection<PhpClass> allControllerClass = PhpClassUtil.getClassByNamespace(PhpIndex.getInstance(project), "\\App\\Controller");
        for (PhpClass phpClass : allControllerClass) {
            // 跳过抽象类
            if (!phpClass.isAbstract()) {
                for (Method method : phpClass.getMethods()) {
                    String className = phpClass.getPresentableFQN();
                    String methodName = method.getName();
                    // 仅 public、非静态、非魔术方法（__construct 等除外）可作为路由 action
                    if (!method.isStatic() && method.getAccess().isPublic() && !methodName.startsWith("__")) {
                        if (!StringUtil.isEmptyOrSpaces(className)) {
                            visitor.visit(phpClass, method);
                        }
                    }
                }
            }
        }
    }


    /** 控制器访问者（按类回调） */
    public interface ControllerVisitor {
        void visit(@NotNull PhpClass phpClass, @NotNull String name);
    }

    /** 控制器 action 访问者（按方法回调） */
    public interface ControllerActionVisitor {
        void visit(@NotNull PhpClass phpClass, @NotNull Method method);
    }
}

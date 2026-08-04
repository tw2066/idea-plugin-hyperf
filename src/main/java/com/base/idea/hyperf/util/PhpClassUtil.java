package com.base.idea.hyperf.util;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.php.PhpIndex;
import com.jetbrains.php.lang.psi.elements.GroupStatement;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import com.jetbrains.php.lang.psi.elements.PhpNamespace;

import java.util.ArrayList;
import java.util.Collection;

/**
 * PhpIndex 辅助工具。
 */
public class PhpClassUtil {


    /**
     * 获取指定命名空间（含所有子命名空间）下的全部类。
     *
     * <p>先取该命名空间直接声明的类，再递归收集子命名空间中的类。
     *
     * @param phpIndex  PHP 索引
     * @param namespace 目标命名空间（如 "\App\Controller"）
     * @return 该命名空间树下的所有 PhpClass
     */
    public static Collection<PhpClass> getClassByNamespace(PhpIndex phpIndex, String namespace) {
        Collection<PhpClass> phpClass = new ArrayList<>();
        // 先循环Class
        PsiElement[] test = null;
        for (PhpNamespace phpNamespace : phpIndex.getNamespacesByName(namespace.toLowerCase())) {
            phpClass.addAll(PsiTreeUtil.getChildrenOfTypeAsList(phpNamespace.getStatements(), PhpClass.class));
        }
        // 再递归收集所有子命名空间中的类
        for (String ns : phpIndex.getChildNamespacesByParentName(namespace + "\\")) {
            phpClass.addAll(getClassByNamespace(phpIndex, namespace + "\\" + ns));
        }
        return phpClass;
    }
}

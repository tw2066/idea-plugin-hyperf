package com.naixiaoxin.idea.hyperf.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.jetbrains.php.lang.psi.elements.FunctionReference;
import com.jetbrains.php.lang.psi.elements.ParameterList;
import fr.adrienbrault.idea.symfony2plugin.codeInsight.utils.PhpElementsUtil;
import fr.adrienbrault.idea.symfony2plugin.util.ParameterBag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/**
 * PSI 元素辅助工具集。
 *
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
public class PsiElementUtils {
    /**
     * getChildren fixed helper
     * 通过 firstChild + nextSibling 链手动收集子元素（绕过 getChildren 在某些 PSI 上的缺陷）
     */
    static public PsiElement[] getChildrenFix(PsiElement psiElement) {
        PsiElement startElement = psiElement.getFirstChild();
        if(startElement == null) {
            return PsiElement.EMPTY_ARRAY;
        }

        List<PsiElement> psiElements = new ArrayList<>();
        psiElements.add(startElement);

        for (PsiElement child = psiElement.getFirstChild().getNextSibling(); child != null; child = child.getNextSibling()) {
            psiElements.add(child);
        }

        return psiElements.toArray(new PsiElement[psiElements.size()]);
    }

    /** 去掉字符串两端的引号（单双引号都处理），null 原样返回 */
    @Nullable
    public static String trimQuote(@Nullable String text) {

        if(text == null) return null;

        return text.replaceAll("^\"|\"$|\'|\'$", "");
    }

    /**
     * 判断元素是否位于指定函数调用的指定参数位置。
     * 向上检查 元素 → ParameterList → FunctionReference 三层结构。
     */
    public static boolean isFunctionReference(@NotNull PsiElement psiElement, @NotNull  String functionName,  int parameterIndex) {

        PsiElement parameterList = psiElement.getParent();
        if(!(parameterList instanceof ParameterList)) {
            return false;
        }

        // 当前元素必须处于目标参数位
        ParameterBag index = PhpElementsUtil.getCurrentParameterIndex(psiElement);
        if(index == null || index.getIndex() != parameterIndex) {
            return false;
        }

        PsiElement functionCall = parameterList.getParent();
        if(!(functionCall instanceof FunctionReference)) {
            return false;
        }

        return functionName.equals(((FunctionReference) functionCall).getName());
    }

    /** 批量将 VirtualFile 转为 PsiFile（找不到的文件跳过） */
    @NotNull
    public static Collection<PsiFile> convertVirtualFilesToPsiFiles(@NotNull Project project, @NotNull Collection<VirtualFile> files) {
        Collection<PsiFile> psiFiles = new HashSet<>();

        PsiManager psiManager = null;
        for (VirtualFile file : files) {
            if(psiManager == null) {
                psiManager = PsiManager.getInstance(project);
            }

            PsiFile psiFile = psiManager.findFile(file);
            if(psiFile != null) {
                psiFiles.add(psiFile);
            }
        }

        return psiFiles;
    }
}

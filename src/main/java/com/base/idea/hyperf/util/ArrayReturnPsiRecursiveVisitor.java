package com.base.idea.hyperf.util;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression;
import com.jetbrains.php.lang.psi.elements.ArrayHashElement;
import com.jetbrains.php.lang.psi.elements.PhpReturn;
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression;
import com.base.idea.hyperf.stub.processor.ArrayKeyVisitor;
import com.intellij.openapi.util.text.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * PHP 文件 return 数组键的递归访问器。
 *
 * <p>在 PSI 树中找到 {@code return [...]} 语句，递归遍历数组的每一层键，
 * 以 "." 连接成完整键路径（如 {@code app.providers.Foo}），逐层回调给
 * {@link ArrayKeyVisitor}。配置索引与翻译索引都通过它收集键。
 *
 * @author NaiXiaoXin(SeanWang) <i@naixiaoxin.com>
 */
public class ArrayReturnPsiRecursiveVisitor extends PsiRecursiveElementWalkingVisitor {

    /** 键前缀（配置文件名 / 翻译命名空间；空串表示无前缀，如 config/config.php） */
    private final String fileNameWithoutExtension;
    private final ArrayKeyVisitor arrayKeyVisitor;

    public ArrayReturnPsiRecursiveVisitor(String fileNameWithoutExtension, ArrayKeyVisitor arrayKeyVisitor) {
        this.fileNameWithoutExtension = fileNameWithoutExtension;
        this.arrayKeyVisitor = arrayKeyVisitor;
    }

    @Override
    public void visitElement(PsiElement element) {

        if (element instanceof PhpReturn) {
            visitPhpReturn((PhpReturn) element);
        }

        super.visitElement(element);
    }

    /** 处理 return 语句：若返回值是数组则开始收集键 */
    public void visitPhpReturn(PhpReturn phpReturn) {
        PsiElement arrayCreation = phpReturn.getFirstPsiChild();
        if (arrayCreation instanceof ArrayCreationExpression) {
            collectConfigKeys((ArrayCreationExpression) arrayCreation, this.arrayKeyVisitor,fileNameWithoutExtension);

        }
    }


    /**
     * 收集入口：根据是否有键前缀选择初始上下文。
     */
    public static void collectConfigKeys(ArrayCreationExpression creationExpression, ArrayKeyVisitor arrayKeyVisitor, String configName) {
        // 兼容当config/config.php的情况 默认无前缀
        if (configName.equals("")){
            List<String> context = Collections.emptyList();
            collectConfigKeys(creationExpression, arrayKeyVisitor, context);
        }else{
            collectConfigKeys(creationExpression, arrayKeyVisitor, Collections.singletonList(configName));
        }
    }

    /**
     * 递归遍历一层数组：对每个字符串键拼出完整键路径（context + 当前键，以 "." 连接）。
     * 值仍为数组时标记 isRootElement=true 并继续下钻；否则标记为叶子键。
     */
    public static void collectConfigKeys(ArrayCreationExpression creationExpression, ArrayKeyVisitor arrayKeyVisitor, List<String> context) {

        for (ArrayHashElement hashElement : PsiTreeUtil.getChildrenOfTypeAsList(creationExpression, ArrayHashElement.class)) {

            PsiElement arrayKey = hashElement.getKey();
            PsiElement arrayValue = hashElement.getValue();

            if (arrayKey instanceof StringLiteralExpression) {
                List<String> myContext = new ArrayList<>(context);
                myContext.add(((StringLiteralExpression) arrayKey).getContents());
                String keyName = StringUtil.join(myContext, ".");

                if (arrayValue instanceof ArrayCreationExpression) {
                    // 中间节点：先回调，再递归子数组
                    arrayKeyVisitor.visit(keyName, arrayKey, true);
                    collectConfigKeys((ArrayCreationExpression) arrayValue, arrayKeyVisitor, myContext);
                } else {
                    // 叶子键
                    arrayKeyVisitor.visit(keyName, arrayKey, false);
                }

            }
        }

    }
}

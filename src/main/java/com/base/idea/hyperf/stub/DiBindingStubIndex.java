package com.base.idea.hyperf.stub;

import com.intellij.psi.PsiFile;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileBasedIndexExtension;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.indexing.ID;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.php.lang.psi.PhpFile;
import com.jetbrains.php.lang.psi.elements.ArrayCreationExpression;
import com.jetbrains.php.lang.psi.elements.ArrayHashElement;
import com.jetbrains.php.lang.psi.elements.ClassConstantReference;
import com.jetbrains.php.lang.psi.elements.PhpReturn;
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.base.idea.hyperf.di.DiBindingValue;
import fr.adrienbrault.idea.symfony2plugin.codeInsight.utils.PhpElementsUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * DI 接口绑定（接口 FQN → 实现 FQN）的文件级索引。
 *
 * <p>索引两类来源：
 * <ul>
 *   <li>项目 {@code config/autoload/dependencies.php}：顶层 {@code IFace::class => Impl::class} 键值对；</li>
 *   <li>任意 {@code ConfigProvider.php}（含 vendor 组件）：__invoke return 数组中
 *       {@code 'dependencies'} 子数组的键值对。</li>
 * </ul>
 * 仅收集键值都是 {@code ::class} 常量的条目（闭包工厂等动态写法不索引）。
 */
public class DiBindingStubIndex extends FileBasedIndexExtension<String, String> {

    /** 索引唯一标识 */
    public static final ID<String, String> KEY = ID.create("com.base.idea.hyperf.di_bindings");

    @NotNull
    @Override
    public ID<String, String> getName() {
        return KEY;
    }

    @NotNull
    @Override
    public DataIndexer<String, String, FileContent> getIndexer() {
        return fileContent -> {
            PsiFile psiFile = fileContent.getPsiFile();
            if (!(psiFile instanceof PhpFile)) {
                return new HashMap<>();
            }
            String path = fileContent.getFile().getPath().replace('\\', '/');
            if (path.endsWith("/config/autoload/dependencies.php")) {
                // 项目绑定：顶层 return 数组直接就是绑定表
                Map<String, String> map = new HashMap<>();
                for (PhpReturn phpReturn : PsiTreeUtil.findChildrenOfType(psiFile, PhpReturn.class)) {
                    if (phpReturn.getFirstPsiChild() instanceof ArrayCreationExpression) {
                        collectClassPairs((ArrayCreationExpression) phpReturn.getFirstPsiChild(), map);
                    }
                }
                return map;
            }
            if ("ConfigProvider.php".equals(fileContent.getFileName())) {
                // 组件绑定：return 数组里 'dependencies' 子数组
                Map<String, String> map = new HashMap<>();
                for (PhpReturn phpReturn : PsiTreeUtil.findChildrenOfType(psiFile, PhpReturn.class)) {
                    if (!(phpReturn.getFirstPsiChild() instanceof ArrayCreationExpression)) {
                        continue;
                    }
                    for (ArrayHashElement hashElement : ((ArrayCreationExpression) phpReturn.getFirstPsiChild()).getHashElements()) {
                        if (hashElement.getKey() instanceof StringLiteralExpression
                                && "dependencies".equals(((StringLiteralExpression) hashElement.getKey()).getContents())
                                && hashElement.getValue() instanceof ArrayCreationExpression) {
                            collectClassPairs((ArrayCreationExpression) hashElement.getValue(), map);
                        }
                    }
                }
                return map;
            }
            return new HashMap<>();
        };
    }

    /** 收集数组中 {@code Xxx::class => Yyy::class / new PriorityDefinition(Yyy::class, n)} 形式的键值对 */
    private static void collectClassPairs(@NotNull ArrayCreationExpression array, @NotNull Map<String, String> map) {
        for (ArrayHashElement hashElement : array.getHashElements()) {
            if (!(hashElement.getKey() instanceof ClassConstantReference)) {
                continue;
            }
            String interfaceFqn = PhpElementsUtil.getClassConstantFqn((ClassConstantReference) hashElement.getKey());
            DiBindingValue value = DiBindingValue.parse(hashElement.getValue());
            if (interfaceFqn != null && value != null) {
                map.put(interfaceFqn, value.toIndexValue());
            }
        }
    }

    @NotNull
    @Override
    public KeyDescriptor<String> getKeyDescriptor() {
        return EnumeratorStringDescriptor.INSTANCE;
    }

    @NotNull
    @Override
    public DataExternalizer<String> getValueExternalizer() {
        return EnumeratorStringDescriptor.INSTANCE;
    }

    /** 只索引 PHP 文件 */
    @NotNull
    @Override
    public FileBasedIndex.InputFilter getInputFilter() {
        return file -> file.getFileType() == PhpFileType.INSTANCE;
    }

    @Override
    public boolean dependsOnFileContent() {
        return true;
    }

    /** 索引版本；结构变化时递增以触发重建（v3：索引值带 PriorityDefinition 形态标志） */
    @Override
    public int getVersion() {
        return 3;
    }
}

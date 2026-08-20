package com.base.idea.hyperf.stub;

import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileBasedIndexExtension;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.indexing.ID;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.VoidDataExternalizer;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.php.lang.psi.PhpFile;
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression;
import com.base.idea.hyperf.cache.CacheListenerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * 缓存监听器名字的文件级索引。
 *
 * <p>索引两类位置的字符串内容（见 {@link CacheListenerUtil}）：
 * {@code #[Cacheable(listener: "...")]}/{@code #[FailCache(...)]} 注解参数，
 * 与 {@code new DeleteListenerEvent("...", ...)} 构造参数。
 * 供监听器名字的双向跳转与补全使用。
 */
public class CacheListenerStubIndex extends FileBasedIndexExtension<String, Void> {

    /** 索引唯一标识 */
    public static final ID<String, Void> KEY = ID.create("com.base.idea.hyperf.cache_listener_names");

    @NotNull
    @Override
    public ID<String, Void> getName() {
        return KEY;
    }

    @NotNull
    @Override
    public DataIndexer<String, Void, FileContent> getIndexer() {
        return fileContent -> {
            final Map<String, Void> map = new HashMap<>();
            PsiFile psiFile = fileContent.getPsiFile();
            if (!(psiFile instanceof PhpFile)) {
                return map;
            }
            psiFile.acceptChildren(new PsiRecursiveElementWalkingVisitor() {
                @Override
                public void visitElement(com.intellij.psi.@NotNull PsiElement element) {
                    if (element instanceof StringLiteralExpression
                            && CacheListenerUtil.isCacheListenerString((StringLiteralExpression) element)) {
                        map.put(((StringLiteralExpression) element).getContents(), null);
                    }
                    super.visitElement(element);
                }
            });
            return map;
        };
    }

    @NotNull
    @Override
    public KeyDescriptor<String> getKeyDescriptor() {
        return EnumeratorStringDescriptor.INSTANCE;
    }

    @NotNull
    @Override
    public DataExternalizer<Void> getValueExternalizer() {
        return VoidDataExternalizer.INSTANCE;
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

    /** 索引版本；结构变化时递增以触发重建 */
    @Override
    public int getVersion() {
        return 1;
    }
}

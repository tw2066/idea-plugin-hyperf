package com.base.idea.hyperf.stub;

import com.intellij.psi.PsiFile;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.VoidDataExternalizer;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.php.lang.psi.PhpFile;
import com.base.idea.hyperf.config.ConfigFileUtil;
import com.base.idea.hyperf.translation.TranslationUtil;
import com.base.idea.hyperf.util.ArrayReturnPsiRecursiveVisitor;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * 翻译键的文件级索引。
 *
 * <p>扫描翻译目录（默认 {@code storage/languages/<lang>/}）下的 PHP 文件，
 * 以文件名作为命名空间前缀，将 return 数组中的键（多级键以 "." 连接）写入索引，
 * 供 {@code TranslationReferences} 做补全与跳转。
 *
 * @author NaiXiaoXin(SeanWang) <i@naixiaoxin.com>
 */
public class TranslationKeyStubIndex extends FileBasedIndexExtension<String, Void> {

    /** 索引唯一标识 */
    public static final ID<String, Void> KEY = ID.create("com.base.idea.hyperf.translation_keys");

    @NotNull
    @Override
    public ID<String, Void> getName() {
        return KEY;
    }

    /**
     * 索引器：对单个文件产出 "键 -> 空值" 的映射。
     * 非 PHP 文件或不在翻译目录下的文件返回空 map。
     */
    @NotNull
    @Override
    public DataIndexer<String, Void, FileContent> getIndexer() {
        return fileContent -> {
            final Map<String, Void> map = new HashMap<>();

            PsiFile psiFile = fileContent.getPsiFile();
            if (!(psiFile instanceof PhpFile)) {
                return map;
            }

            // 由文件路径解析翻译命名空间（即文件名），不在翻译目录则为 null
            String namespace = TranslationUtil.getNamespaceFromFilePath(fileContent.getFile().getPath(), fileContent.getProject());
            if (namespace == null) {
                return map;
            }

            // 遍历 return 数组，所有键（含中间节点）都入索引
            psiFile.acceptChildren(new ArrayReturnPsiRecursiveVisitor(
                    namespace, (key, psiKey, isRootElement) -> map.put(key, null))
            );

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

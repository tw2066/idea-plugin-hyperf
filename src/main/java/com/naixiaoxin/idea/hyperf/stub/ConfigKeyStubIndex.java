package com.naixiaoxin.idea.hyperf.stub;

import com.intellij.psi.PsiFile;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.VoidDataExternalizer;
import com.jetbrains.php.lang.PhpFileType;
import com.jetbrains.php.lang.psi.PhpFile;
import com.naixiaoxin.idea.hyperf.config.ConfigFileUtil;
import com.naixiaoxin.idea.hyperf.util.ArrayReturnPsiRecursiveVisitor;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * 配置键的文件级索引。
 *
 * <p>扫描所有 Hyperf 配置文件（{@code config/autoload/*.php} 与 {@code config/config.php}），
 * 将其 return 数组中的键（多级键以 "." 连接，并带配置文件前缀）写入索引，
 * 供 {@code ConfigReferences} 做补全与跳转。
 *
 * @author Daniel Espendiller <daniel@espendiller.net>
 * @author NaiXiaoXin(SeanWang) <i@naixiaoxin.com>
 */
public class ConfigKeyStubIndex extends FileBasedIndexExtension<String, Void> {

    /** 索引唯一标识 */
    public static final ID<String, Void> KEY = ID.create("om.naixiaoxin.idea.hyperf.config_keys");

    @NotNull
    @Override
    public ID<String, Void> getName() {
        return KEY;
    }

    /**
     * 索引器：对单个文件产出 "键 -> 空值" 的映射。
     * 非 PHP 文件或非配置文件返回空 map。
     */
    @NotNull
    @Override
    public DataIndexer<String, Void, FileContent> getIndexer() {
        return fileContent -> {
            final Map<String, Void> map = new HashMap<>();

            PsiFile psiFile = fileContent.getPsiFile();
            if(!(psiFile instanceof PhpFile)) {
                return map;
            }

            // 判断是否为配置文件并取键前缀（如 "app"）
            ConfigFileUtil.ConfigFileMatchResult result = ConfigFileUtil.matchConfigFile(fileContent.getProject(), fileContent.getFile());

            if(result.matches()) {

                // 遍历 return 数组，收集所有叶子键（中间节点 isRootElement=true 不入索引）
                psiFile.acceptChildren(new ArrayReturnPsiRecursiveVisitor(result.getKeyPrefix(), (key, psiKey, isRootElement) -> {
                    if (!isRootElement) {
                        map.put(key, null);
                    }
                }));
            }

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

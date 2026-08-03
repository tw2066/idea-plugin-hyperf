package com.naixiaoxin.idea.hyperf.stub;

import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.VoidDataExternalizer;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * .env 环境变量键的文件级索引。
 *
 * <p>扫描项目根目录下的 {@code .env} / {@code .env.*} 文件（纯文本，非 PHP），
 * 按行解析 {@code KEY=value}，将 KEY 写入索引，
 * 供 {@code EnvReferences} 做 {@code env()} 键的补全与跳转。
 *
 * @author NaiXiaoXin(SeanWang) <i@naixiaoxin.com>
 */
public class EnvKeyStubIndex extends FileBasedIndexExtension<String, Void> {

    /** 索引唯一标识 */
    public static final ID<String, Void> KEY = ID.create("om.naixiaoxin.idea.hyperf.env_keys");

    @NotNull
    @Override
    public ID<String, Void> getName() {
        return KEY;
    }

    /**
     * 索引器：对单个 .env 文件产出 "键 -> 空值" 的映射。
     * 逐行解析，跳过注释（# 开头）与空行；只取第一个 "=" 前的部分作为键。
     */
    @NotNull
    @Override
    public DataIndexer<String, Void, FileContent> getIndexer() {
        return fileContent -> {
            final Map<String, Void> map = new HashMap<>();

            String content = fileContent.getContentAsText().toString();
            for (String line : content.split("\\R")) {
                String key = parseKey(line);
                if (key != null) {
                    map.put(key, null);
                }
            }

            return map;
        };
    }

    /**
     * 解析一行的环境变量键。
     *
     * @return 键名；注释、空行、无 "=" 的行返回 null
     */
    public static String parseKey(@NotNull String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
            return null;
        }
        // 兼容 "export KEY=value" 写法
        if (trimmed.startsWith("export ")) {
            trimmed = trimmed.substring("export ".length()).trim();
        }
        int eq = trimmed.indexOf('=');
        if (eq <= 0) {
            return null;
        }
        String key = trimmed.substring(0, eq).trim();
        return key.isEmpty() ? null : key;
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

    /** 只索引项目根目录下的 .env / .env.* 文件（排除 vendor 等目录） */
    @NotNull
    @Override
    public FileBasedIndex.InputFilter getInputFilter() {
        return file -> {
            String name = file.getName();
            if (!name.equals(".env") && !name.startsWith(".env.")) {
                return false;
            }
            // 只收项目根目录下的 .env（其父目录名不应是 vendor 等）；简单起见排除 vendor 路径
            return !file.getPath().contains("/vendor/");
        };
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

package com.base.idea.hyperf.stub;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import com.base.idea.hyperf.util.HyperfRootUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * .env 环境变量键的文件级索引。
 *
 * <p>只索引 Hyperf 应用根目录下的一个 {@code .env} 文件（纯文本，非 PHP）：
 * 优先 {@code .env}，若不存在则回退到第一个 {@code .env.*}（如 {@code .env.example}）。
 * 按行解析 {@code KEY=value}，键写入索引、值作为索引 value 存储（供补全列表展示），
 * 供 {@code EnvReferences} 做 {@code env()} 键的补全与跳转。
 * 应用根由 {@link HyperfRootUtil} 解析，支持 Hyperf 在项目子目录的场景。
 *
 * @author NaiXiaoXin(SeanWang) <i@naixiaoxin.com>
 */
public class EnvKeyStubIndex extends FileBasedIndexExtension<String, String> {

    /** 索引唯一标识 */
    public static final ID<String, String> KEY = ID.create("com.base.idea.hyperf.env_keys");

    @NotNull
    @Override
    public ID<String, String> getName() {
        return KEY;
    }

    /**
     * 索引器：对选中的 .env 文件产出 "键 -> 值" 的映射。
     * 逐行解析，跳过注释（# 开头）与空行；键取第一个 "=" 前的部分，值取其后并去首尾引号。
     * 非项目根目录或非"优先文件"时返回空 map。
     */
    @NotNull
    @Override
    public DataIndexer<String, String, FileContent> getIndexer() {
        return fileContent -> {
            final Map<String, String> map = new HashMap<>();

            VirtualFile file = fileContent.getFile();
            VirtualFile rootDir = HyperfRootUtil.resolve(fileContent.getProject());
            if (rootDir == null || file.getParent() == null || !file.getParent().equals(rootDir)) {
                return map;
            }
            // 只索引"优先选中的那一个" .env 文件
            VirtualFile target = selectEnvFile(rootDir);
            if (target == null || !target.equals(file)) {
                return map;
            }

            String content = fileContent.getContentAsText().toString();
            for (String line : content.split("\\R")) {
                String key = parseKey(line);
                if (key != null) {
                    map.put(key, parseValue(line));
                }
            }

            return map;
        };
    }

    /**
     * 项目根目录下应被索引的 .env 文件：优先 {@code .env}，不存在则取第一个 {@code .env.*}。
     */
    @Nullable
    public static VirtualFile selectEnvFile(@NotNull VirtualFile baseDir) {
        VirtualFile dotEnv = baseDir.findChild(".env");
        if (dotEnv != null && !dotEnv.isDirectory()) {
            return dotEnv;
        }
        for (VirtualFile child : baseDir.getChildren()) {
            String name = child.getName();
            if (!child.isDirectory() && name.startsWith(".env.")) {
                return child;
            }
        }
        return null;
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

    /**
     * 解析一行的环境变量值（供补全列表展示）。
     * 去掉首尾空白与成对的单/双引号；未加引号的值按 dotenv 惯例去掉 " #" 后的行内注释。
     *
     * @return 值文本；无 "=" 的行返回空串
     */
    @NotNull
    public static String parseValue(@NotNull String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("export ")) {
            trimmed = trimmed.substring("export ".length()).trim();
        }
        int eq = trimmed.indexOf('=');
        if (eq < 0) {
            return "";
        }
        String value = trimmed.substring(eq + 1).trim();
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        int comment = value.indexOf(" #");
        if (comment >= 0) {
            value = value.substring(0, comment).trim();
        }
        return value;
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

    /** 放行 .env / .env.* 候选文件；是否项目根目录与优先级由索引器决定 */
    @NotNull
    @Override
    public FileBasedIndex.InputFilter getInputFilter() {
        return file -> {
            String name = file.getName();
            return name.equals(".env") || name.startsWith(".env.");
        };
    }

    @Override
    public boolean dependsOnFileContent() {
        return true;
    }

    /** 索引版本；结构变化时递增以触发重建（v4：索引 value 从 Void 改为存变量值） */
    @Override
    public int getVersion() {
        return 4;
    }
}

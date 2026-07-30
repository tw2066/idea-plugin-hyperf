package com.naixiaoxin.idea.hyperf.stub.processor;

import com.intellij.psi.PsiElement;

/**
 * 数组键访问器（回调接口）。
 *
 * <p>在遍历 PHP 数组结构（如配置/翻译文件 return 的嵌套数组）时，
 * 每遇到一个数组键便回调一次 {@link #visit}。
 *
 * @author Daniel Espendiller <daniel@espendiller.net>
 * @author NaiXiaoXin(SeanWang) <i@naixiaoxin.com>
 */
public interface ArrayKeyVisitor {
    /**
     * @param key           完整键名（多级键以 "." 连接，如 "logger.default.name"）
     * @param psiKey        键对应的 PSI 元素（用于跳转定位）
     * @param isRootElement 该键是否仍包含子数组（true 表示是中间节点而非叶子）
     */
    void visit(String key, PsiElement psiKey, boolean isRootElement);
}

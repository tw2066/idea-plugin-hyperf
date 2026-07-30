package com.naixiaoxin.idea.hyperf.stub.processor;

import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Processor;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.ID;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * 索引键收集器（{@link Processor} 实现）。
 *
 * <p>配合 {@code FileBasedIndex.processAllKeys} 遍历某索引的全部键并去重收集，
 * {@link #getResult()} 再过滤掉在当前项目范围内已无对应文件的键，
 * 最终返回项目中真实存在的键集合，用于补全列表。
 *
 * @author Daniel Espendiller <daniel@espendiller.net>
 * @author NaiXiaoXin(SeanWang) <i@naixiaoxin.com>
 */
public class CollectProjectUniqueKeys implements Processor<String> {

    @NotNull
    private final Project project;

    @NotNull
    private final ID id;

    /** 已收集的键（去重） */
    @NotNull
    private final Set<String> stringSet;

    public CollectProjectUniqueKeys(@NotNull Project project, @NotNull ID id) {
        this.project = project;
        this.id = id;
        this.stringSet = new HashSet<>();
    }

    /** processAllKeys 回调：每个键调用一次，返回 true 继续遍历 */
    @Override
    public boolean process(String s) {
        this.stringSet.add(s);
        return true;
    }

    /**
     * 返回在项目中确实存在（仍有文件包含该键）的键集合。
     * 过滤掉那些索引里残留但文件已删除/失效的键。
     */
    public Set<String> getResult() {
        Set<String> set = new HashSet<>();

        for (String key : stringSet) {
            Collection fileCollection = FileBasedIndex.getInstance().getContainingFiles(id, key, GlobalSearchScope.allScope(project));
            if (fileCollection.size() > 0) {
                set.add(key);
            }
        }

        return set;
    }

    /** 便捷静态方法：遍历指定索引并返回项目中存在的全部键 */
    @NotNull
    public static Set<String> collect(@NotNull Project project, @NotNull ID<String, ?> id) {
        CollectProjectUniqueKeys collector = new CollectProjectUniqueKeys(project, id);
        FileBasedIndex.getInstance().processAllKeys(id, collector, project);
        return collector.getResult();
    }
}
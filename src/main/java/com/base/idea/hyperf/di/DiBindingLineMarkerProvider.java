package com.base.idea.hyperf.di;

import com.base.idea.hyperf.HyperfIcons;
import com.base.idea.hyperf.HyperfStartupActivity;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.awt.RelativePoint;
import com.jetbrains.php.PhpIndex;
import com.jetbrains.php.lang.psi.elements.ClassReference;
import com.jetbrains.php.lang.psi.elements.Field;
import com.jetbrains.php.lang.psi.elements.ParameterList;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import com.jetbrains.php.lang.psi.elements.PhpTypeDeclaration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * DI 绑定的 gutter 图标（hyperf-di-mark.svg），点击跳到 Dependencies 映射的实现类。
 *
 * <p>两类位置：接口声明名旁；类型声明处（{@link PhpTypeDeclaration} 内的类引用，
 * 如 {@code #[Inject] private FooInterface $x}、构造器参数）解析目标是接口且存在生效绑定。
 * 同一行有多个绑定（如一行多个构造器参数）时只在首个绑定上显示一个图标，点击弹列表选择。
 * 绑定解析走索引，故不做 DumbAware。
 */
public class DiBindingLineMarkerProvider implements LineMarkerProvider {

    /** 行内一个绑定目标：用于去重判断的引用元素 + 展示名 + 生效绑定 */
    private static class Target {
        final PsiElement ref;
        final String label;
        final DiBindingValue value;

        Target(PsiElement ref, String label, DiBindingValue value) {
            this.ref = ref;
            this.label = label;
            this.value = value;
        }
    }

    @Override
    public @Nullable LineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
        if (element instanceof PhpClass) {
            PhpClass phpClass = (PhpClass) element;
            if (!phpClass.isInterface() || phpClass.getNameIdentifier() == null || phpClass.getFQN() == null) {
                return null;
            }
            DiBindingValue winner = resolveBinding(phpClass.getNameIdentifier(), phpClass.getFQN());
            if (winner == null) {
                return null;
            }
            return createMarker(phpClass.getNameIdentifier(),
                    Collections.singletonList(new Target(phpClass.getNameIdentifier(), phpClass.getName(), winner)));
        }
        if (element instanceof ClassReference) {
            // 只标类型声明内的类引用（字段/参数类型；联合类型的引用嵌在类型声明内部），
            // 排除 use/继承/注解/属性默认值等处的引用
            if (PsiTreeUtil.getParentOfType(element, PhpTypeDeclaration.class) == null) {
                return null;
            }
            PsiElement resolved = ((ClassReference) element).resolve();
            if (!(resolved instanceof PhpClass) || !((PhpClass) resolved).isInterface()
                    || ((PhpClass) resolved).getFQN() == null) {
                return null;
            }
            DiBindingValue winner = resolveBinding(element, ((PhpClass) resolved).getFQN());
            if (winner == null) {
                return null;
            }
            // 行内去重：同一容器（参数列表/字段）同一行只保留首个绑定的图标
            PsiElement container = PsiTreeUtil.getParentOfType(element, ParameterList.class, Field.class);
            Document document = PsiDocumentManager.getInstance(element.getProject())
                    .getDocument(element.getContainingFile());
            if (container == null || document == null) {
                return createMarker(element, Collections.singletonList(
                        new Target(element, element.getText(), winner)));
            }
            List<Target> lineTargets = collectLineTargets(container, document.getLineNumber(element.getTextOffset()));
            if (lineTargets.isEmpty() || lineTargets.get(0).ref != element) {
                return null;
            }
            return createMarker(element, lineTargets);
        }
        return null;
    }

    /** 收集容器内与指定行同一行的全部生效绑定（文档序） */
    @NotNull
    private static List<Target> collectLineTargets(@NotNull PsiElement container, int line) {
        Document document = PsiDocumentManager.getInstance(container.getProject())
                .getDocument(container.getContainingFile());
        List<Target> targets = new ArrayList<>();
        if (document == null) {
            return targets;
        }
        for (ClassReference ref : PsiTreeUtil.findChildrenOfType(container, ClassReference.class)) {
            if (PsiTreeUtil.getParentOfType(ref, PhpTypeDeclaration.class) == null
                    || document.getLineNumber(ref.getTextOffset()) != line) {
                continue;
            }
            PsiElement resolved = ref.resolve();
            if (!(resolved instanceof PhpClass) || !((PhpClass) resolved).isInterface()
                    || ((PhpClass) resolved).getFQN() == null) {
                continue;
            }
            DiBindingValue winner = resolveBinding(ref, ((PhpClass) resolved).getFQN());
            if (winner != null) {
                targets.add(new Target(ref, ref.getText(), winner));
            }
        }
        return targets;
    }

    @Nullable
    private static DiBindingValue resolveBinding(@NotNull PsiElement anchor, @NotNull String interfaceFqn) {
        if (!HyperfStartupActivity.isEnabled(anchor.getProject())) {
            return null;
        }
        return DiBindingResolver.resolveWinningBinding(
                anchor.getProject(), StringUtil.trimStart(interfaceFqn, "\\"));
    }

    @NotNull
    private static LineMarkerInfo<?> createMarker(@NotNull PsiElement anchor, @NotNull List<Target> targets) {
        Project project = anchor.getProject();
        String tooltip = targets.size() == 1
                ? "Dependencies: \\" + targets.get(0).value.implFqn
                : "Dependencies: " + targets.size() + " bindings";
        return new LineMarkerInfo<>(
                anchor,
                anchor.getTextRange(),
                HyperfIcons.DI,
                e -> tooltip,
                (GutterIconNavigationHandler<PsiElement>) (event, elt) -> onGutterClick(project, targets, event),
                GutterIconRenderer.Alignment.RIGHT,
                () -> tooltip
        );
    }

    private static void onGutterClick(@NotNull Project project, @NotNull List<Target> targets,
                                      @NotNull MouseEvent event) {
        if (targets.size() == 1) {
            navigateToImpl(project, targets.get(0).value);
            return;
        }
        JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<Target>("Dependencies", targets) {
            @Override
            public @NotNull String getTextFor(Target value) {
                return value.label + " → \\" + value.value.implFqn;
            }

            @Override
            public PopupStep<?> onChosen(Target selectedValue, boolean finalChoice) {
                navigateToImpl(project, selectedValue.value);
                return FINAL_CHOICE;
            }
        }).show(new RelativePoint(event));
    }

    private static void navigateToImpl(@NotNull Project project, @NotNull DiBindingValue binding) {
        Collection<PhpClass> classes = PhpIndex.getInstance(project).getAnyByFQN("\\" + binding.implFqn);
        for (PhpClass phpClass : classes) {
            phpClass.navigate(true);
            return;
        }
    }
}

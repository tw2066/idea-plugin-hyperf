package fr.adrienbrault.idea.symfony2plugin.codeInsight.completion;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import com.base.idea.hyperf.HyperfStartupActivity;
import fr.adrienbrault.idea.symfony2plugin.codeInsight.GotoCompletionContributor;
import fr.adrienbrault.idea.symfony2plugin.codeInsight.GotoCompletionProviderInterface;
import fr.adrienbrault.idea.symfony2plugin.codeInsight.utils.GotoCompletionUtil;
import org.jetbrains.annotations.NotNull;

public class CompletionContributor extends com.intellij.codeInsight.completion.CompletionContributor {

    /**
     * 字符串字面量内输入时允许自动弹出补全。
     * 是否有建议仍由各 References 的匹配决定，无建议时平台不会弹窗。
     */
    @Override
    public boolean invokeAutoPopup(@NotNull PsiElement position, char typeChar) {
        if (position.getParent() instanceof com.jetbrains.php.lang.psi.elements.StringLiteralExpression
                && Character.isLetter(typeChar)) {
            return true;
        }
        return super.invokeAutoPopup(position, typeChar);
    }

    public CompletionContributor() {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), new CompletionProvider<CompletionParameters>() {
            @Override
            protected void addCompletions(@NotNull CompletionParameters completionParameters, ProcessingContext processingContext, @NotNull CompletionResultSet completionResultSet) {

                PsiElement psiElement = completionParameters.getOriginalPosition();
                if (psiElement == null || !HyperfStartupActivity.isEnabled(psiElement)) {
                    return;
                }

                CompletionContributorParameter parameter = null;

                for(GotoCompletionContributor contributor: GotoCompletionUtil.getContributors(psiElement)) {
                    GotoCompletionProviderInterface formReferenceCompletionContributor = contributor.getProvider(psiElement);
                    if(formReferenceCompletionContributor != null) {
                        completionResultSet.addAllElements(
                            formReferenceCompletionContributor.getLookupElements()
                        );

                        if(parameter == null) {
                            parameter = new CompletionContributorParameter(completionParameters, processingContext, completionResultSet);
                        }

                        formReferenceCompletionContributor.getLookupElements(parameter);
                    }
                }

            }
        });
    }

}

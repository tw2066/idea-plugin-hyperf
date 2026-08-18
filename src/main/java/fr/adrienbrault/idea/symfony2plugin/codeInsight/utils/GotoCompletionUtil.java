package fr.adrienbrault.idea.symfony2plugin.codeInsight.utils;

import com.intellij.psi.PsiElement;
import com.base.idea.hyperf.aop.AspectReferences;
import com.base.idea.hyperf.config.ConfigReferences;
import com.base.idea.hyperf.controller.ControllerReferences;
import com.base.idea.hyperf.env.EnvReferences;
import com.base.idea.hyperf.path.BasePathReferences;
import com.base.idea.hyperf.translation.TranslationReferences;
import com.base.idea.hyperf.validation.ValidationReferences;
import com.base.idea.hyperf.view.ViewReferences;
import fr.adrienbrault.idea.symfony2plugin.codeInsight.GotoCompletionContributor;
import fr.adrienbrault.idea.symfony2plugin.codeInsight.GotoCompletionLanguageRegistrar;
import fr.adrienbrault.idea.symfony2plugin.codeInsight.GotoCompletionRegistrar;
import fr.adrienbrault.idea.symfony2plugin.codeInsight.GotoCompletionRegistrarParameter;

import java.util.ArrayList;
import java.util.Collection;

public class GotoCompletionUtil {

    private static GotoCompletionRegistrar[] CONTRIBUTORS = new GotoCompletionRegistrar[]{
            new ControllerReferences(),
            new ConfigReferences(),
            new TranslationReferences(),
            new EnvReferences(),
            new ValidationReferences(),
            new BasePathReferences(),
            new ViewReferences(),
            new AspectReferences(),

    };

    public static Collection<GotoCompletionContributor> getContributors(final PsiElement psiElement) {
        Collection<GotoCompletionContributor> contributors = new ArrayList<>();

        GotoCompletionRegistrarParameter registrar = (pattern, contributor) -> {
            if (pattern.accepts(psiElement)) {
                contributors.add(contributor);
            }
        };

        for (GotoCompletionRegistrar register : CONTRIBUTORS) {
            // filter on language
            if (register instanceof GotoCompletionLanguageRegistrar) {
                if (((GotoCompletionLanguageRegistrar) register).support(psiElement.getLanguage())) {
                    register.register(registrar);
                }
            } else {
                register.register(registrar);
            }
        }

        return contributors;
    }
}

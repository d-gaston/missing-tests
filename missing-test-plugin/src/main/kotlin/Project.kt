import com.intellij.lang.jvm.JvmModifier
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiAnonymousClass
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AllClassesSearch

fun Project.testClasses(): Collection<PsiClass> {
    val scope = GlobalSearchScope.projectScope(this)
    return AllClassesSearch.search(scope, this)
        .findAll().filter { it.isTestClass() }
}
val Project.interfaces: Collection<PsiClass>
    get() = applicationClasses().filter { it.isInterface }

fun Project.applicationClasses(): Collection<PsiClass> {
    val scope = GlobalSearchScope.projectScope(this)
    return AllClassesSearch.search(scope, this)
        .findAll().filter { !it.isTestClass() }
}
fun Project.allClasses(): Collection<PsiClass> {
    val scope = GlobalSearchScope.projectScope(this)
    return AllClassesSearch.search(scope, this).findAll()
}
val Project.candidateClasses
    get() = applicationClasses()
        .filterNot { it is PsiAnonymousClass }.filterNot { it.hasModifier(JvmModifier.PRIVATE) }
        .filterNot { it.hasModifier(JvmModifier.STATIC) }
        .filterNot { it.name?.contains("Test")?:false }
        //.filterNot { it.hasModifier(JvmModifier.ABSTRACT) }
        //.filterNot { it.containingClass?.isInterface?:false }
        .filter { it.containingClass?.containingClass == null }

import com.intellij.lang.jvm.JvmModifier
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.util.PsiClassUtil
import com.intellij.testIntegration.TestFinderHelper

fun PsiClass.inheritors() : Collection<PsiClass> {
    return ClassInheritorsSearch.search(this).findAll()
        .filterNot { it is PsiAnonymousClass }.filter { it.containingClass == null }
        .filterNot { it.isInterface }.filterNot {it.hasModifier(JvmModifier.ABSTRACT)}
}
val PsiClass.getPackage
    get() = JavaDirectoryService.getInstance().getPackage(this.containingFile.containingDirectory)

fun PsiClass.methodsWithAnnotation(annotation: String):Collection<PsiMethod>{
    return this.methods.filter { it.annotations.map { it.text }.contains(annotation) }
}

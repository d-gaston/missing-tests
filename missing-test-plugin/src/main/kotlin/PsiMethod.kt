import com.intellij.execution.PsiLocation
import com.intellij.execution.junit.JUnitUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiNewExpression

fun PsiMethod.isTest(checkAbstract: Boolean = true, checkRunWith: Boolean = true, checkClass: Boolean = true) =
    JUnitUtil.isTestMethod(PsiLocation(this), checkAbstract, checkRunWith, checkClass)

fun PsiMethod.allCalls():Collection<PsiMethod>{
    return this.calls + this.methodsToInline.flatMap { it.calls }
}
fun PsiMethod.allObjects():Collection<PsiClass>{
    return this.objects + this.methodsToInline.flatMap { it.objects }
}
val PsiMethod.calls:Collection<PsiMethod>
    get() = this.find<PsiMethodCallExpression>().mapNotNull { it.resolveMethod() }

val PsiMethod.objects:Collection<PsiClass>
    get() = this.find<PsiNewExpression>().mapNotNull { it.resolveMethod() }.mapNotNull { it.containingClass }

val PsiMethod.methodsToInline:Collection<PsiMethod>
    get() = this.calls.filter { it.containingClass == this.containingClass }.union(
            (this.containingClass?.methodsWithAnnotation("@Before") ?: emptyList())+
            (this.containingClass?.methodsWithAnnotation("@After") ?: emptyList()) +
            (this.containingClass?.methods?.filter { it.name == "setUp" }?: emptyList()))



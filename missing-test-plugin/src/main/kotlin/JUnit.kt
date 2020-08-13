import com.intellij.execution.junit.JUnitUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod


fun PsiClass.isTestClass(checkForAbstract: Boolean = false, checkForTestCaseInheritance: Boolean = true) =
    JUnitUtil.isTestClass(this, checkForAbstract, checkForTestCaseInheritance)


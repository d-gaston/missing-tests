import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

inline fun <reified T : PsiElement> PsiElement.find() = PsiTreeUtil.collectElementsOfType(this, T::class.java)
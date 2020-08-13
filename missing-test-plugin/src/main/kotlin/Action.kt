import com.google.common.collect.HashMultimap
import com.google.gson.GsonBuilder
import com.intellij.lang.jvm.JvmModifier
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.ui.Messages
import com.intellij.psi.*
import com.intellij.psi.search.searches.OverridingMethodsSearch
import java.io.File


val BASE_DIR = File("..").getCanonicalPath()
var projectName = ""
val methodToTests: HashMultimap<PsiMethod, PsiMethod> = HashMultimap.create()
val siblingToMissing = HashMultimap.create<PsiMethod, Report>()
val sharesAttributes = HashMultimap.create<Boolean, PsiMethod>()
var siblingsWithTests = 0

val outputFile = File("$BASE_DIR${File.separator}reports.json")
//extra data about siblings
val siblingInfoFile = File("$BASE_DIR${File.separator}sibling-info.json")

/**
 * container that is used to hold the info that will
 * be written out to json
 */
data class Report(
    val subject: String,
    val type: String,
    val category: Int,
    val pkg: String,
    val cls: String,
    val sibling: String,
    val summary: List<String>,
    val hasTests: Boolean,
    val support: Pair<Int,Int>,
    val originTests: List<String>,
    val hasThisTest: List<String>
)

data class test(
    val subject: String,
    val pkg:String,
    val cls: String,
    val sibling: String,
    val tests: List<String>
)


/**
 * Writes the results out to a json file whose name is the same as
 * the project's name
 */
class Write : AnAction("Output Results to JSON") {
    override fun actionPerformed(event: AnActionEvent) {
        val gson = GsonBuilder().disableHtmlEscaping().create()
        siblingToMissing.asMap().forEach { psiMethod, set ->
            if (methodToTests[psiMethod].size > 0) {
                siblingInfoFile.appendText("${gson.toJson(test(projectName,
                    psiMethod.containingClass?.getPackage.toString().replace(
                    "PsiPackage:",
                    ""),
                            psiMethod.containingClass?.name ?: "", psiMethod.hierarchicalMethodSignature.toString().replace("HierarchicalMethodSignatureImpl: ", ""),
                            methodToTests[psiMethod].map { "${it.containingClass?.name}:${it.name}" }))}\n"
                )
            }
            /**
             * This prevents the same report from being generated for a sibling,
             * which happens if the sibling is part of multiple groups
             */
            for ((_, groupedByType) in set.groupBy { it.type }) {
                for ((_, groupedByAttribute) in groupedByType.groupBy { it.summary }) {
                    outputFile.appendText(gson.toJson(groupedByAttribute.sortedBy { it.support.second }.last()) + "\n")
                }
            }
        }
    }
}

/**
 * Same as Write above, but prints results out to console with statistics
 */
class Display : AnAction("Output Results To Console") {
    override fun actionPerformed(event: AnActionEvent) {
        var count = 0
        siblingToMissing.asMap().forEach { psiMethod, set ->
            println("${psiMethod.containingClass}.${psiMethod.name} missing")

            for ((_, groupedByType) in set.groupBy { it.type }) {
                for ((_, groupedByAttribute) in groupedByType.groupBy { it.summary }) {
                    println("\t" + groupedByAttribute.sortedBy { it.support.second }.last())
                    count++
                }
            }
            println("has")
            methodToTests[psiMethod].forEach {
                println("\t ${it.containingClass} ${it.name}")

            }

            println("\t^^^^^^^^^^^^^")
            println()

        }
        /**
         * Some extra info
         */
        println("${sharesAttributes[true].size} siblings share at least 1 majority attribute")
        println("${sharesAttributes[false].minus(sharesAttributes[true]).size} siblings share no majority attributes")
        println("${siblingsWithTests} siblings with tests")
        println("Total missing = $count")

    }
}

data class summaries(
    val subject: List<String>,
    val response: List<String>,
    val modifiers: List<String>,
    val body: Set<String>,
    val sibling: PsiMethod,
    val test: PsiMethod
)

/**
 * Runs the actual plugin
 */
class Action : AnAction("Run Plugin") {
    /**
     * Gets the M_ethod C_all S_et of a test
     */
    fun extractMCS(test: PsiMethod): Set<String> {
        return test.allCalls()
            .map { it.hierarchicalMethodSignature.toString().replace("HierarchicalMethodSignatureImpl: ", "") }.sorted()
            .toSet()
    }


    /**
     * updates the map of siblings to reports
     * for all siblings that do not have the test
     */
    fun reportMissing(
        type: String,
        siblings: List<PsiMethod>,
        summaryList: List<summaries>,
        summary: List<String>,
        size: Int,
        groupSize: Int
    ) {
        siblings.minus(summaryList.map { it.sibling })
            .forEach {
                siblingToMissing.put(
                    it,
                    Report(projectName,
                        type,
                        -1,
                        it.containingClass?.getPackage.toString().replace("PsiPackage:", ""),
                        it.containingClass?.name ?: "",
                        it.hierarchicalMethodSignature.toString().replace(
                            "HierarchicalMethodSignatureImpl: ",
                            ""
                        ),
                        summary,
                        methodToTests[it].size > 0,
                        Pair(size, groupSize),
                        summaryList.map {
                            "${it.test.containingClass?.getPackage.toString().replace(
                                "PsiPackage:",
                                ""
                            )}/${it.test.containingClass?.name}.${it.test.hierarchicalMethodSignature.toString().replace(
                                "HierarchicalMethodSignatureImpl: ",
                                ""
                            )}"
                        },
                        summaryList.map {
                            "${it.sibling.containingClass?.getPackage.toString().replace(
                                "PsiPackage:",
                                ""
                            )}/${it.sibling.containingClass?.name}"
                        })
                )
            }
    }

    /**
     * Determines which siblings are missing tests based on the name
     * and body summaries. Uses a simple majority as the reporting threshold
     */
    fun findMissing(siblings: List<PsiMethod>) {
        val siblingsWithTests = siblings.filter { methodToTests[it].size > 0 }
        val summaryList = mutableListOf<summaries>()
        for (sibling in siblings) {
            summaryList.addAll(methodToTests[sibling].map { test ->
                val m = test.name.words.getSRM()
                summaries(
                    m["subject"]!!, m["response"]!!
                    , m["modifiers"]!!, extractMCS(test), sibling, test)
            })
        }
        for (s in summaryList) {
            val name = summaryList.filter {
                s.subject.isPrefixOf(it.subject) &&
                        s.response.isPrefixOf(it.response) &&
                        s.modifiers.isPrefixOf(it.modifiers)
            }
            val body = summaryList.filter {
                it.body.containsAll(s.body)
            }
            val nameSize = name.map { it.sibling }.toSet().size
            val bodySize = body.map { it.sibling }.toSet().size

            if (nameSize > siblingsWithTests.size / 2.0) {
                reportMissing("name", siblings, name, s.subject.map { "subject:$it" } +
                        s.response.map { "response:$it" } + s.modifiers.map { "modifier:$it" },
                    nameSize, siblingsWithTests.size
                )

            }
            if (bodySize > siblingsWithTests.size / 2.0) {
                reportMissing(
                    "body", siblings, body, s.body.toList(),
                    bodySize, siblingsWithTests.size
                )
            }
        }


    }

    /**
     * Runs the plugin
     */
    override fun actionPerformed(event: AnActionEvent) {
        siblingToMissing.clear()
        sharesAttributes.clear()
        methodToTests.clear()
        val lStartTime = System.currentTimeMillis()
        val project = event.getData(PlatformDataKeys.PROJECT)!!
        projectName = project.name
        val allTestMethods = project.testClasses()
            .flatMap { it.methods.filter { it.isTest() } }
        allTestMethods.forEach { test -> test.allCalls().forEach { methodToTests.put(it, test) } }

        val allMethods = project.candidateClasses.flatMap { it.methods.toList() }

        println("# test classes: ${project.testClasses().size}")
        println("# test methods: ${allTestMethods.size}")
        println("#  classes: ${project.applicationClasses().size}")
        println("#  methods: ${project.applicationClasses().flatMap { it.methods.toList() }.size}")
       /**
        * Creates a map of a method to itself plus all methods that override it, then
        * filters out inappropriate methods such as methods in inner classes
        * or private methods
        */
       val groups = allMethods.associate { method ->
            method to ((OverridingMethodsSearch.search(method).findAll() + method)
                .filter { it.containingClass?.containingClass == null }
                .filterNot { it.containingClass is PsiAnonymousClass }
                .filterNot { it.containingClass?.name?.contains("Mock") ?: false }
                .filterNot { it.containingClass?.hasModifier(JvmModifier.PRIVATE) ?: false }
                .filterNot { it.containingClass?.hasModifier(JvmModifier.ABSTRACT) ?: false }
                .filterNot { it.containingClass?.isInterface ?: false }
                .filterNot { it.containingClass?.hasModifier(JvmModifier.STATIC) ?: false })


        }.filter { it.value.filter { methodToTests[it].size > 0 }.size > 2 }
        siblingsWithTests = groups.values.flatten().toSet().filter { methodToTests[it].size > 0 }.size
       /**
        * Various statistics about the siblings and groups in the project
        */
        println("Groups ${groups.size}")
        println("overriders with tests ${allMethods.flatMap {
            OverridingMethodsSearch.search(it).findAll().filter { methodToTests[it].size > 0 }
        }.size}")
        println("Siblings with tests ${siblingsWithTests}")
        println("Siblings  ${groups.values.flatten().toSet().size}")

        for ((_, siblings) in groups) {
            findMissing(siblings)
        }
        println("=======================")

        val output = System.currentTimeMillis() - lStartTime
        println("Elapsed time in seconds: ${output / 1000}")
        Messages.showMessageDialog(project, "Done", "Plugin", Messages.getInformationIcon())
    }

}
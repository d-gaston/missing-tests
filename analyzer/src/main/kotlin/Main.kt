import com.google.common.collect.HashMultimap
import java.io.File
import com.google.gson.GsonBuilder
import org.jsoup.Jsoup
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.streams.toList



/**
 * Holds an entry of the plugin's output. The fields reflect the json schema
 */
data class Report(
    val subject: String,             //the application under test
    var type: String,                //the type of report
    var category: Int,               //Missing Test (0), Candidate for Refactoring(3) or Erroneous Report(2)
    val pkg: String,
    val cls: String,
    val sibling: String,            //method signature
    var summary: List<String>,      //either list of words (name summary) or set of method calls (body summary)
    val hasTests: Boolean,
    val support: Pair<Int, Int>,    //Pair(supports the summary, total siblings in group)
    var originTests: List<String>,  //from which tests the summary was generated
    val hasThisTest: List<String>   //siblings that have the test
)


val gson = GsonBuilder().disableHtmlEscaping().create()
val BASE_DIR = File("..").getCanonicalPath()
val reports = File("$BASE_DIR${File.separator}reports.json").readLines().map { gson.fromJson(it, Report::class.java) }

/**
 * Holds coverage information for a single method, along with whether or not
 * that method has a report generated for it
 * !!!
 * WARNING: Branch coverage can be n/a so this value must be filtered
 * if you want to use it
 * !!!
 */
data class cov(val project: String,val path: String, val method:String, val insCov: Int, var branchCov: String, var hasReport: Boolean = false)

/**
 * Puts the Jacoco HTML reports into a list of cov objects, and checks
 * each method for a report in order to set cov's field appropriately
 */
fun matchCoverageToReports() {
    val projects = reports.map { it.subject }.toSet()
    val covList = mutableListOf<cov>()
    val siblingPathToCov = mutableMapOf<String,cov>()
    for (p in projects) {

        val coverageFiles = Files.walk(Paths.get("$BASE_DIR${File.separator}jacoco${File.separator}$p"))
            .filter { Files.isRegularFile(it) }
            .filter { it.toString().endsWith(".java.html") }
            .filter { !it.toString().contains("Test") }
            .map { it }.toList()

        val files = coverageFiles.map { File(it.toString().replace("java.", "")) }
        for (f in files) {
            try {

                val doc = Jsoup.parse(f, null)

                /**
                 * Plumbing operations to dig out the necessary info
                 * from jacoco reports
                 *
                 */
                val table = doc.select("table").get(0)
                val rows = table.select("tr")
                for (row in rows) {
                    val cols = row.select("td")
                    val method = cols.select("[id^=a]").text()
                    if ( method.contains("(")) {
                        val instrCoverage = cols.select("[id^=c]").text().replace("%", "")
                        val branchCov = cols.select("[id^=e]").text().replace("%", "")

                            val path = "${f.toString().replace(".html", "")
                                .replace("$BASE_DIR${File.separator}jacoco/", "")}.$method"
                        val c = cov(p,path,method, instrCoverage.toInt(), branchCov)
                        siblingPathToCov.put(path,c)
                        covList.add(c)
                    }

                }
            } catch (e: Exception) {
                //println(e)
            }
        }
    }
    val siblingPathToReports = HashMultimap.create<String,Report>()
    reports.filter {  it.category == 0 }.forEach { r->
        siblingPathToReports.put( "${r.subject}/${r.pkg}/${r.cls}.${r.sibling
            /**
             * this wranglings is done to get the IntelliJ representation of method names to
             * match that of jacoco
             */
            .replace("PsiType:", "")
            .replace("([","(")
            .replace("])",")")
            .replace("<(.*?)>(.*?)>".toRegex(), "")
            .replace("<(.*?)>".toRegex(), "")}", r)

    }
    siblingPathToReports.keySet().forEach {
        siblingPathToCov[it]?.hasReport=true
    }


    /**
     * Table 3 data
     */
    covList.groupBy { it.project }.forEach {(project,l)->
        println(project)
        println("\tFully covered ${l.filter { it.insCov == 100 }.size }")
        println("\tPartially covered ${l.filter { it.insCov in 1..99 }.size}")
        println("\tNot covered ${l.filter { it.insCov ==0}.size}")
    }

    /**
     * Figure 5 data
     */
    var fullyCoveredReports = 0
    var partiallyCoveredReports = 0
    var unCoveredReports = 0
    siblingPathToReports.asMap().forEach { (path,reports)->
        when(siblingPathToCov[path]?.insCov){
            100 -> fullyCoveredReports+=reports.size
            in 1..99 -> partiallyCoveredReports+=reports.size
            0 -> unCoveredReports+=reports.size
        }
    }
    println("Reports whose siblings are fully covered: $fullyCoveredReports")
    println("Reports whose siblings are partially covered: $partiallyCoveredReports")
    println("Reports whose siblings are not covered: $unCoveredReports")





}

fun reportsToCSV() {
    PrintWriter(File("$BASE_DIR${File.separator}csv${File.separator}agg.csv").bufferedWriter()).use { writer ->
        writer.println("project,type,category,support")
        reports.forEach {
            var category = ""
            when(it.category){
                0->category = "MT"
                2->category = "ER"
                3->category = "CR"
            }
            writer.println("${it.subject},${it.type},$category,${it.support.first/it.support.second.toDouble()}")
        }
    }
}

/**
 * computes the jaccard similarity between name summaries of reports and body
 * summaries of reports
 */
fun similarity(){


    val (nameReports, bodyReports) = reports
        .partition { it.type == "name" }
    assert(nameReports.first().type == "name")

    fun jaccardSimilarity(s1: Iterable<*>, s2: Iterable<*>): Double {
        val intersection = s1.intersect(s2)
        val union = s1.union(s2)
        return intersection.size.toDouble() / union.size.toDouble()
    }
    PrintWriter(File("$BASE_DIR${File.separator}csv${File.separator}similarity.csv").bufferedWriter()).use { writer ->

        writer.println("type,category,overlap")
        for (report in nameReports) {
            val similarity = bodyReports
                .filter {
                    report.subject == it.subject &&
                            report.pkg == it.pkg &&
                            report.cls == it.cls &&
                            report.sibling == it.sibling
                }
                .map { jaccardSimilarity(report.originTests, it.originTests) }
                .max() ?: 0.0

            writer.println("${report.type},${report.category},$similarity")
        }

        for (report in bodyReports) {
            val similarity = nameReports
                .filter {
                    report.subject == it.subject &&
                            report.pkg == it.pkg &&
                            report.cls == it.cls &&
                            report.sibling == it.sibling
                }
                .map { jaccardSimilarity(report.originTests, it.originTests) }
                .max() ?: 0.0

            writer.println("${report.type},${report.category},$similarity")
        }
    }
}
fun main() {
    println(BASE_DIR)
    similarity()
    reportsToCSV()
    matchCoverageToReports()
}
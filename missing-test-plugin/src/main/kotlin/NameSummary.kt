import com.google.common.base.CharMatcher
import com.google.common.base.Splitter

/**
 * isPrefixOf is for determining subsumption of summaries
 *
 */
fun List<String>.isPrefixOf(l:List<String>):Boolean{
    if (this.size > l.size) return false
    if(this == l || this.isEmpty()) return true
    for(i in 0..this.size-1){
        if(l[i] != this[i]) return false
    }
    return true
}

/**
 * Returns a map of the test name's subject, reponse, and modifier words
 * It starts with subject, then switches to response and modifier if one of the
 * words in the respective lists is matched
 * If no response or modifer is found, the whole test name is deemed to be the subject
 */
fun List<String>.getSRM():Map<String,List<String>>{
    val r = listOf("should","then","is","are","will","returns","must","does","can")
    val s = listOf("test")
    val m = listOf("when","where","for",
        "with","in","on","upon","given","if","uses","has","have","after","that","requires","as")
    val srm =
        mutableMapOf("subject" to mutableListOf<String>(),
            "response" to mutableListOf<String>(),
            "modifiers" to mutableListOf<String>())
    var type = "subject"
    for(it in this){

        if(s.contains(it)) {type = "subject";continue}
        if(r.contains(it)) {type = "response";continue}
        if(m.contains(it))  {type = "modifiers";continue}

        srm[type]!!.add(it)
    }
    if(srm["subject"]!!.isEmpty()) srm["subject"]!!.add("it")
    return srm
}

/**
 * Helper string methods for the test names
 */
val String.words get() =
    basicSplit(this).split(" ").filterNot { it.isNumeric }.map { it.toLowerCase() }
val String.isNumeric get() = this.matches("-?\\d+(\\.\\d+)?".toRegex())
fun basicSplit(_id: String): String {
    var id = _id
    //# remove punctuation
    id = id.replace("[^a-zA-Z0-9]".toRegex(), " ")
    // Separate numbers not already sep. by underscore
    id = id.replace("([a-zA-Z])([0-9])".toRegex(), "$1 $2")
    id = id.replace("([0-9])([a-zA-Z])".toRegex(), "$1 $2")
    // default camel case
    id = id.replace("([a-z])([A-Z])".toRegex(), "$1 $2")
    // Issue: plural acronyms
    // APIs: AP Is or APIs?
    id = id.replace("([A-Z])([A-Z][a-z])".toRegex(), "$1 $2")
    //# Remove any 2+ space -> 1 space
    id = id.replace("\\s+".toRegex(), " ")

    return id
}

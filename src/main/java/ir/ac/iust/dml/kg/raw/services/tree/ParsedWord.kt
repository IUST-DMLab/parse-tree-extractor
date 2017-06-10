package ir.ac.iust.dml.kg.raw.services.tree

data class ParsedWord(
    var position: Int = 0,
    var word: String? = null,
    var lemma: String? = null,
    var pos: String? = null,
    var features: String? = null,
    var head: Int = 0,
    var relation: String? = null,
    var cPOS: String? = null
)

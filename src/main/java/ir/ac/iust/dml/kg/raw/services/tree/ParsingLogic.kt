package ir.ac.iust.dml.kg.raw.services.tree

import edu.stanford.nlp.ling.TaggedWord
import ir.ac.iust.dml.kg.raw.DependencyParser
import ir.ac.iust.dml.kg.raw.services.access.entities.DependencyPattern
import ir.ac.iust.dml.kg.raw.services.access.repositories.DependencyPatternRepository
import ir.ac.iust.dml.kg.raw.services.access.repositories.OccurrenceRepository
import org.apache.commons.logging.LogFactory
import org.bson.types.ObjectId
import org.maltparser.concurrent.graph.ConcurrentDependencyGraph
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class ParsingLogic {
  @Autowired lateinit private var dao: OccurrenceRepository
  @Autowired lateinit private var patternDao: DependencyPatternRepository
  private val logger = LogFactory.getLog(javaClass)
  private val DEP_CALC_ERROR = "error"

  fun findOne(id: String) = dao.findOne(ObjectId(id))

  fun searchPattern(page: Int, pageSize: Int, maxSentenceLength: Int?,
                    minSize: Int?, approved: Boolean?)
      = patternDao.search(page, pageSize, maxSentenceLength, minSize, approved)

  fun save(pattern: DependencyPattern) = patternDao.save(pattern)

  fun dependencyText(text: String) = convert(DependencyParser.parseRaw(text))

  fun dependencySentence(sentence: String) = convert(DependencyParser.parseRaw(sentence))[0]

  fun dependencySentences(sentences: List<String>) =
      convert(DependencyParser.parseRaw(sentences.joinToString(separator = " ")))

  fun convert(graphs: List<ConcurrentDependencyGraph>) = graphs.map { convert(it) }

  fun convert(graph: ConcurrentDependencyGraph): List<ParsedWord> {
    val words = mutableListOf<ParsedWord>()
    for (i in 1..graph.nTokenNodes()) {
      val node = graph.getDependencyNode(i)
      val parsedWord = ParsedWord()
      parsedWord.position = Integer.parseInt(node.getLabel("ID"))
      parsedWord.word = node.getLabel("FORM")
      parsedWord.lemma = node.getLabel("LEMMA")
      parsedWord.cPOS = node.getLabel("CPOSTAG")
      parsedWord.pos = node.getLabel("POSTAG")
      parsedWord.features = node.getLabel("FEATS")
      val headIdLabel = node.head.getLabel("ID")
      parsedWord.head = if (headIdLabel.isEmpty()) 0 else Integer.parseInt(headIdLabel)
      parsedWord.relation = node.getLabel("DEPREL")
      words.add(parsedWord)
    }
    return words
  }

  fun writeSizes() {
    var page = 0
    do {
      val pages = patternDao.search(page++, 100, null, null, null)
      pages.forEach {
        it.count = it.samples.size
        it.sentenceLength = it.pattern.split("][").size
        patternDao.save(it)
      }
    } while (!pages.isLast)
  }

  fun writeParses() {
    val hashes = mutableMapOf<String, MutableSet<String>>()
    var uniqueSentenceCount = 0
    var page = 0
    val startTime = System.currentTimeMillis()
    do {
      val pages = dao.search(page++, 100, null, false, null, null, null, null)
      pages.filter { it.posTags != null && it.posTags.isNotEmpty() && it.posTags.size < 20 }.forEach {
        try {
          if (it.depTreeHash == null) {
            val posTags = mutableListOf<TaggedWord>()
            it.words.forEachIndexed { index, word -> posTags.add(TaggedWord(word, it.posTags[index])) }
            val depTree = DependencyParser.parse(posTags)
            if (depTree != null) {
              val hashBuilder = StringBuilder()
              for (index in 1..depTree.nTokenNodes()) {
                val token = depTree.getTokenNode(index)
                hashBuilder.append('[')
                    .append(token.getLabel("POSTAG"))
                    .append(',').append(token.headIndex)
                    .append(',').append(token.getLabel("DEPREL"))
                    .append(']')
              }
              it.depTreeHash = hashBuilder.toString()
            } else it.depTreeHash = DEP_CALC_ERROR
            dao.save(it)
          }
          if (it.depTreeHash != DEP_CALC_ERROR) {
            val set = hashes.getOrPut(it.depTreeHash, { mutableSetOf() })
            if (!set.contains(it.raw)) {
              uniqueSentenceCount++
              set.add(it.raw)
            }
            if (uniqueSentenceCount % 1000 == 0)
              logger.info("${uniqueSentenceCount} >> ${hashes.size} ($page  of ${pages.totalPages}) " +
                  "in ${(System.currentTimeMillis() - startTime) / 1000} seconds")
          }
        } catch (th: Throwable) {
          logger.error(it.raw, th)
        }
      }
    } while (!pages.isLast)

//    hashes.filter { it.value.size > 1 }
//        .map { Triple(it.key, it.value, it.value.size) }
//        .sortedByDescending { it.third }
//        .forEach {
//          println("${it.third} - ${it.first}")
//          if(it.second.size < 10) it.second.forEach { sentence -> println(sentence) }
//        }

    var writtenPatterns = 0
    hashes.forEach { pattern, sentences ->
      writtenPatterns++
      val e = patternDao.findByPattern(pattern) ?: DependencyPattern(pattern)
      e.samples.addAll(sentences)
      patternDao.save(e)
      if (writtenPatterns % 1000 == 0)
        logger.info("$writtenPatterns of ${hashes.size} patterns has been written")
    }
  }

  fun test() = "test"
}

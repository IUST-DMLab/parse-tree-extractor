package ir.ac.iust.dml.kg.raw.services.tree

import ir.ac.iust.dml.kg.raw.services.access.entities.Occurrence
import ir.ac.iust.dml.kg.raw.services.access.repositories.OccurrenceRepository
import org.bson.types.ObjectId
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class ParsingLogic {
  @Autowired
  private val dao: OccurrenceRepository? = null

  fun findOne(id: String): Occurrence {
    return dao!!.findOne(ObjectId(id))
  }

  fun save(occurrence: Occurrence) {
    dao!!.save(occurrence)
  }

  fun writeParses() {
    val ali = dao!!.search(0, 20, null, false, null, null, null, null)
    ali.forEach { it -> println(it.raw) }
  }

  fun test() = "test"
}

/*
 * Farsi Knowledge Graph Project
 *  Iran University of Science and Technology (Year 2018)
 *  Developed by Majid Asgari.
 */

/*
 * Farsi Knowledge Graph Project
 *  Iran University of Science and Technology (Year 2018)
 *  Developed by Majid Asgari.
 */

package ir.ac.iust.dml.kg.raw.services.unsupervised;

import ir.ac.iust.dml.kg.raw.DependencyParser;
import ir.ac.iust.dml.kg.raw.Normalizer;
import ir.ac.iust.dml.kg.raw.SentenceBranch;
import ir.ac.iust.dml.kg.raw.SimpleConstituencyParser;
import ir.ac.iust.dml.kg.raw.extractor.EnhancedEntityExtractor;
import ir.ac.iust.dml.kg.raw.extractor.ResolvedEntityToken;
import ir.ac.iust.dml.kg.raw.triple.RawTriple;
import ir.ac.iust.dml.kg.raw.triple.RawTripleBuilder;
import ir.ac.iust.dml.kg.raw.triple.RawTripleExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class UnsupervisedTripleExtractor implements RawTripleExtractor {

  private EnhancedEntityExtractor enhancedEntityExtractor = new EnhancedEntityExtractor();
  private static final Logger logger = LoggerFactory.getLogger(UnsupervisedTripleExtractor.class);

  @SuppressWarnings("Duplicates")
  @Override
  public List<RawTriple> extract(String source, String version, String text) {
    List<RawTriple> result;
    List<List<ResolvedEntityToken>> sentences = enhancedEntityExtractor.extract(
        SentenceBranch.summarize(Normalizer.removeBrackets(Normalizer.normalize(text))));
    enhancedEntityExtractor.disambiguateByContext(sentences, 3, 0.0001f);
    result = extract(source, version, sentences);
    return result;
  }

  private String constituencyToString(List<ResolvedEntityToken> tokens) {
    final Set<String> iris = new HashSet<>();
    int numberOfResources = 0;

    final StringBuilder builder = new StringBuilder();
    for (ResolvedEntityToken token : tokens) {
      if (token.getResource() != null) {
        iris.add(token.getResource().getIri());
        numberOfResources++;
      }
      if (token.getPos().equals("PUNC")) continue;
      if (token.getShrunkWords() == null)
        builder.append(token.getWord()).append(' ');
      else {
        for (ResolvedEntityToken shrunkWord : token.getShrunkWords()) {
          if (!token.getPos().equals("PUNC"))
            builder.append(shrunkWord.getWord()).append(' ');
        }
      }
    }
    if ((iris.size() == 1) && (numberOfResources == tokens.size()))
      return iris.iterator().next();
    if (builder.length() > 0) {
      builder.setLength(builder.length() - 1);
      return builder.toString();
    }
    return null;
  }

  @Override
  public List<RawTriple> extract(String source, String version, List<List<ResolvedEntityToken>> text) {
    final List<RawTriple> triples = new ArrayList<>();
    final RawTripleBuilder builder = new RawTripleBuilder("unsupervised", source,
        System.currentTimeMillis(), version);
    for (List<ResolvedEntityToken> sentence : text) {
      try {
        if (sentence.size() > 0 && sentence.size() < 40) {
          sentence = enhancedEntityExtractor.shrinkNameEntities(sentence);
          if (sentence.get(0).getDep() == null) DependencyParser.addDependencyParse(sentence, true);
          if (sentence.get(0).getPhraseMates() == null) SimpleConstituencyParser.addConstituencyParse(sentence);
          logger.info("checking this sentence: " + SimpleConstituencyParser.tokensToString(sentence));
          List<List<ResolvedEntityToken>> constituencies = new ArrayList<>();
          List<ResolvedEntityToken> lastGroup = new ArrayList<>();
          lastGroup.add(sentence.get(0));
          for (int i = 1; i < sentence.size(); i++) {
            final ResolvedEntityToken token = sentence.get(i);
            if (!token.getPhraseMates().contains(i - 1)) {
              if (lastGroup.size() > 0) constituencies.add(lastGroup);
              lastGroup = new ArrayList<>();
            }
            lastGroup.add(token);
          }
          if (lastGroup.size() > 0) constituencies.add(lastGroup);

          final List<List<ResolvedEntityToken>> effectiveCons = new ArrayList<>();
          for (List<ResolvedEntityToken> c : constituencies) {
            boolean hasNameOrVerb = false;
            for (ResolvedEntityToken t : c) {
              if (t.getPos().equals("N") || t.getPos().equals("Ne") || t.getPos().equals("V")) {
                hasNameOrVerb = true;
                break;
              }
            }
            if (hasNameOrVerb) effectiveCons.add(c);
          }

          if (effectiveCons.size() > 2) {
            boolean moreThanOneVerbs = false;
            List<ResolvedEntityToken> verbConstituency = null;
            List<List<ResolvedEntityToken>> entityConstituencies = new ArrayList<>();
            List<List<ResolvedEntityToken>> otherConstituencies = new ArrayList<>();
            for (List<ResolvedEntityToken> constituency : effectiveCons) {
              final Set<String> set = new HashSet<>();
              int resourceSize = 0;
              for (ResolvedEntityToken token : constituency) {
                if (token.getResource() != null && !token.getResource().getMainClass().endsWith("Thing")) {
                  set.add(token.getResource().getIri());
                  resourceSize++;
                }
                if (token.getPos().equals("V")) {
                  if (verbConstituency != null) moreThanOneVerbs = true;
                  verbConstituency = constituency;
                }
              }
              if (constituency != verbConstituency) {
//                if (set.size() == 1 && resourceSize == constituency.size()) {
                if (set.size() >= 1) {
                  if (!set.iterator().next().endsWith("Thing"))
                    entityConstituencies.add(constituency);
//                  allEntityCons.add(constituency);
                } else otherConstituencies.add(constituency);
              }
            }
            if (!moreThanOneVerbs && entityConstituencies.size() == 2 && verbConstituency != null) {
              RawTriple triple = builder.create()
                  .subject(constituencyToString(entityConstituencies.get(0)))
                  .predicate(constituencyToString(verbConstituency))
                  .object(constituencyToString(entityConstituencies.get(1)))
                  .rawText(constituencyToString(sentence))
                  .accuracy(0.75).needsMapping(true);
              for (int i = 0; i < otherConstituencies.size(); i++)
                triple.getMetadata().put("extra" + i, constituencyToString(otherConstituencies.get(i)));
              triples.add(triple);
            }
            if (!moreThanOneVerbs && entityConstituencies.size() == 1 &&
                otherConstituencies.size() > 0 && verbConstituency != null) {
              for (List<ResolvedEntityToken> otherConstituency : otherConstituencies) {
                boolean hasName = hasName(otherConstituency);
                if (hasName) {
                  RawTriple triple = builder.create()
                      .subject(constituencyToString(entityConstituencies.get(0)))
                      .predicate(constituencyToString(verbConstituency))
                      .object(constituencyToString(otherConstituency))
                      .rawText(constituencyToString(sentence))
                      .accuracy(0.7).needsMapping(true);
                  for (int i = 0; i < otherConstituencies.size(); i++)
                    if (otherConstituencies.get(i) != otherConstituency)
                      triple.getMetadata().put("extra" + i, constituencyToString(otherConstituencies.get(i)));
                  triples.add(triple);
                }
              }
            }
//            if (!moreThanOneVerbs && allEntityCons.size() >= 2 && verbConstituency != null) {
//              for (int i1 = 0; i1 < allEntityCons.size(); i1++) {
//                List<ResolvedEntityToken> otherConstituency = allEntityCons.get(i1);
//                boolean hasName = hasName(otherConstituency);
//                if (hasName) {
//                  for (int i2 = 0; i2 < allEntityCons.size(); i2++) {
//                    List<ResolvedEntityToken> otherConstituency2 = allEntityCons.get(i2);
//                    if(i1 != i2) {
//                      RawTriple triple = builder.create()
//                          .subject(constituencyToString(otherConstituency))
//                          .predicate(constituencyToString(verbConstituency))
//                          .object(constituencyToString(otherConstituency2))
//                          .rawText(constituencyToString(sentence))
//                          .accuracy(0.5).needsMapping(true);
//                      for (int i3 = 0; i3 < allEntityCons.size(); i3++)
//                        if (allEntityCons.get(i3) != otherConstituency)
//                          triple.getMetadata().put("extra" + i3, constituencyToString(allEntityCons.get(i3)));
//                      triples.add(triple);
//                    }
//                  }
//                }
//              }
//            }
          }
        }
      } catch (Throwable th) {
        logger.error("error in extracting from sentence", th);
      }
    }
    return triples;
  }

  private boolean hasName(List<ResolvedEntityToken> otherConstituency) {
    boolean hasName = false;
    for (ResolvedEntityToken t : otherConstituency) {
      if (t.getPos().equals("N") || t.getPos().equals("Ne")) {
        hasName = true;
        break;
      }
    }
    return hasName;
  }
}

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
import ir.ac.iust.dml.kg.raw.extractor.IobType;
import ir.ac.iust.dml.kg.raw.extractor.ResolvedEntityToken;
import ir.ac.iust.dml.kg.raw.extractor.ResolvedEntityTokenResource;
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
    enhancedEntityExtractor.resolveByName(sentences);
    enhancedEntityExtractor.resolvePronouns(sentences);
    result = extract(source, version, sentences);
    return result;
  }

  private boolean isResolvedPronoun(ResolvedEntityToken resolvedEntityToken) {
    if (resolvedEntityToken.getResource() == null) return false;
    final String word = resolvedEntityToken.getWord();
    return word.equals("او") || word.equals("وی") ||
            word.equals("اینجا") || word.equals("آنجا") ||
            word.equals("آن‌ها") || word.equals("آنها");
  }

  private String getTitle(ResolvedEntityTokenResource resource) {
    final int index = resource.getIri().lastIndexOf('/');
    final String title = resource.getIri().substring(index + 1);
    return title.replace('_', ' ');
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
      if (token.getShrunkWords() == null) {
        if (isResolvedPronoun(token)) builder.append(getTitle(token.getResource())).append(' ');
        else builder.append(token.getWord()).append(' ');
      } else {
        for (ResolvedEntityToken shrunkWord : token.getShrunkWords()) {
          if (!token.getPos().equals("PUNC")) {
            builder.append(shrunkWord.getWord()).append(' ');
          }
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
    final RawTripleBuilder builder = new RawTripleBuilder("unsupervised_raw", source,
            System.currentTimeMillis(), version);
    final RawTripleBuilder recklessBuilder = new RawTripleBuilder("reckless_raw", source,
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
//              int resourceSize = 0;
              for (ResolvedEntityToken token : constituency) {
                if (token.getResource() != null && !token.getResource().getMainClass().endsWith("Thing")) {
                  set.add(token.getResource().getIri());
//                  resourceSize++;
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
                } else otherConstituencies.add(constituency);
              }
            }
            if (!moreThanOneVerbs && entityConstituencies.size() == 2 && verbConstituency != null) {
              boolean subjectHasP = hasPOS(entityConstituencies.get(0), true, "P");
              RawTriple triple;
              List<ResolvedEntityToken> subject = entityConstituencies.get(0);
              List<ResolvedEntityToken> object = entityConstituencies.get(1);

              if(!subjectHasP) {
                triple = builder.create()
                        .subject(constituencyToString(subject))
                        .predicate(constituencyToString(verbConstituency))
                        .object(constituencyToString(object))
                        .rawText(constituencyToString(sentence))
                        .accuracy(calculateAccuracy(0.7, subject, object))
                        .needsMapping(true);
              } else {
                triple = recklessBuilder.create()
                        .subject(constituencyToString(subject))
                        .predicate(constituencyToString(verbConstituency))
                        .object(constituencyToString(object))
                        .rawText(constituencyToString(sentence))
                        .accuracy(calculateAccuracy(0.4, subject, object))
                        .needsMapping(true);
              }
              for (int i = 0; i < otherConstituencies.size(); i++)
                triple.getMetadata().put("extra" + i, constituencyToString(otherConstituencies.get(i)));
              triples.add(triple);
            }
            if (!moreThanOneVerbs && entityConstituencies.size() == 1 &&
                    otherConstituencies.size() > 0 && otherConstituencies.size() < 3 && verbConstituency != null) {
              for (List<ResolvedEntityToken> otherConstituency : otherConstituencies) {
                boolean hasName = hasPOS(otherConstituency,  false,"N", "Ne");
                boolean subjectHasP = hasPOS(entityConstituencies.get(0), true, "P");
                List<ResolvedEntityToken> subject = entityConstituencies.get(0);
                if (hasName) {
                  RawTriple triple;
                  if(!subjectHasP) {
                    triple = builder.create()
                            .subject(constituencyToString(subject))
                            .predicate(constituencyToString(verbConstituency))
                            .object(constituencyToString(otherConstituency))
                            .rawText(constituencyToString(sentence))
                            .accuracy(calculateAccuracy(0.6, subject, otherConstituency))
                            .needsMapping(true);
                  } else {
                    triple = recklessBuilder.create()
                            .subject(constituencyToString(subject))
                            .predicate(constituencyToString(verbConstituency))
                            .object(constituencyToString(otherConstituency))
                            .rawText(constituencyToString(sentence))
                            .accuracy(calculateAccuracy(0.3, subject, otherConstituency))
                            .needsMapping(true);
                  }
                  for (int i = 0; i < otherConstituencies.size(); i++)
                    if (otherConstituencies.get(i) != otherConstituency)
                      triple.getMetadata().put("extra" + i, constituencyToString(otherConstituencies.get(i)));
                  triples.add(triple);
                }
              }
            }
            if (!moreThanOneVerbs && effectiveCons.size() >= 2 && verbConstituency != null &&
                    otherConstituencies.size() < 4) {
              for (int i1 = 0; i1 < effectiveCons.size(); i1++) {
                List<ResolvedEntityToken> c1 = effectiveCons.get(i1);
                if (c1 == verbConstituency) continue;
                for (int i2 = i1 + 1; i2 < effectiveCons.size(); i2++) {
                  List<ResolvedEntityToken> c2 = effectiveCons.get(i2);
                  if (i1 != i2 && c2 != verbConstituency) {
                    RawTriple triple = recklessBuilder.create()
                            .subject(constituencyToString(c1))
                            .predicate(constituencyToString(verbConstituency))
                            .object(constituencyToString(c2))
                            .rawText(constituencyToString(sentence))
                            .accuracy(calculateAccuracy(0.3, c1, c2)).needsMapping(true);
                    for (int i3 = 0; i3 < constituencies.size(); i3++)
                      if (constituencies.get(i3) != c1 && constituencies.get(i3) != c2 &&
                              constituencies.get(i3) != verbConstituency)
                        triple.getMetadata().put("extra" + i3, constituencyToString(constituencies.get(i3)));
                    triples.add(triple);
                  }
                }
              }
            }
          }
        }
      } catch (Throwable th) {
        logger.error("error in extracting from sentence", th);
      }
    }
    return triples;
  }

  private double calculateAccuracy(double base, List<ResolvedEntityToken> subject, List<ResolvedEntityToken> object) {
    return base + (numberOfResources(subject) * 0.01) + (numberOfResources(object) * 0.005) +
            (allEntitiesIsPOS(subject, "N", "Ne") ? 0.1 : 0) +
            (allEntitiesIsPOS(object, "N", "Ne") ? 0.05 : 0);
  }

  private int numberOfResources(List<ResolvedEntityToken> tokens) {
    int count = 0;
    for (ResolvedEntityToken t : tokens)
      if(t.getResource() != null) count++;
    return count;
  }

  private boolean hasPOS(List<ResolvedEntityToken> tokens, boolean justSearchInOutside, String ... posTags) {
    for (ResolvedEntityToken t : tokens) {
      for(String posTag: posTags) {
        if(t.getPos().equals(posTag) && (!justSearchInOutside || t.getIobType() == IobType.Outside)) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean allEntitiesIsPOS(List<ResolvedEntityToken> tokens, String ... posTags) {
    for (ResolvedEntityToken t : tokens) {
      boolean matched = false;
      for(String posTag: posTags) {
        if(t.getPos().equals(posTag))
          matched = true;
      }
      if(!matched)
        return false;
    }
    return true;
  }
}

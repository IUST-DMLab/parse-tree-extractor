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
import ir.ac.iust.dml.kg.raw.SimpleConstituencyParser;
import ir.ac.iust.dml.kg.raw.extractor.EnhancedEntityExtractor;
import ir.ac.iust.dml.kg.raw.extractor.ResolvedEntityToken;
import ir.ac.iust.dml.kg.raw.triple.RawTriple;
import ir.ac.iust.dml.kg.raw.triple.RawTripleBuilder;
import ir.ac.iust.dml.kg.raw.triple.RawTripleExtractor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class UnsupervisedTripleExtractor implements RawTripleExtractor {

  private EnhancedEntityExtractor enhancedEntityExtractor = new EnhancedEntityExtractor();

  @SuppressWarnings("Duplicates")
  @Override
  public List<RawTriple> extract(String source, String version, String text) {
    List<RawTriple> result;
    List<List<ResolvedEntityToken>> sentences = enhancedEntityExtractor.extract(text);
    enhancedEntityExtractor.disambiguateByContext(sentences, 0, 00001f);
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
      if (token.getShrunkWords() == null)
        builder.append(token.getWord()).append(' ');
      else {
        for (ResolvedEntityToken shrunkWord : token.getShrunkWords()) {
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
      if (sentence.size() > 0) {
        if (sentence.get(0).getDep() == null) DependencyParser.addDependencyParse(sentence);
        if (sentence.get(0).getPhraseMates() == null) SimpleConstituencyParser.addConstituencyParse(sentence);
        List<List<ResolvedEntityToken>> constituencies = new ArrayList<>();
        List<ResolvedEntityToken> lastGroup = new ArrayList<>();
        lastGroup.add(sentence.get(0));
        for (int i = 1; i < sentence.size(); i++) {
          final ResolvedEntityToken token = sentence.get(i);
          if (!token.getPhraseMates().contains(i - 1)) {
            if (lastGroup.size() > 0) constituencies.add(lastGroup);
            lastGroup = new ArrayList<>();
          }
          if (!token.getPos().equals("CONJ") && !token.getPos().equals("PUNC")) lastGroup.add(token);
        }
        if (lastGroup.size() > 0) constituencies.add(lastGroup);

        if (constituencies.size() > 2) {
          boolean moreThanOneVerbs = false;
          List<ResolvedEntityToken> verbConstituency = null;
          List<List<ResolvedEntityToken>> entityConstituencies = new ArrayList<>();
          List<List<ResolvedEntityToken>> otherConstituencies = new ArrayList<>();
          for (List<ResolvedEntityToken> constituency : constituencies) {
            final Set<String> set = new HashSet<>();
            int resourceSize = 0;
            for (ResolvedEntityToken token : constituency) {
              if (token.getResource() != null) {
                set.add(token.getResource().getIri());
                resourceSize++;
              }
              if (token.getPos().equals("V")) {
                if (verbConstituency != null) moreThanOneVerbs = true;
                verbConstituency = constituency;
              }
            }
            if (constituency != verbConstituency) {
              if (set.size() == 1 && resourceSize == constituency.size() &&
                  !constituency.get(0).getResource().getMainClass().endsWith("Thing"))
                entityConstituencies.add(constituency);
              else otherConstituencies.add(constituency);
            }
          }
          if (!moreThanOneVerbs && entityConstituencies.size() == 2 && verbConstituency != null) {
            RawTriple triple = builder.create()
                .subject(constituencyToString(entityConstituencies.get(0)))
                .predicate(constituencyToString(verbConstituency))
                .object(constituencyToString(entityConstituencies.get(1)))
                .rawText(constituencyToString(sentence))
                .accuracy(0.5).needsMapping(true);
            for (int i = 0; i < otherConstituencies.size(); i++)
              triple.getMetadata().put("extra" + i, constituencyToString(otherConstituencies.get(i)));
            triples.add(triple);
          }
          if (!moreThanOneVerbs && entityConstituencies.size() == 1 &&
              otherConstituencies.size() > 0 && verbConstituency != null) {
            for (List<ResolvedEntityToken> otherConstituency : otherConstituencies) {
              RawTriple triple = builder.create()
                  .subject(constituencyToString(entityConstituencies.get(0)))
                  .predicate(constituencyToString(verbConstituency))
                  .object(constituencyToString(otherConstituency))
                  .rawText(constituencyToString(sentence))
                  .accuracy(0.5).needsMapping(true);
              for (int i = 0; i < otherConstituencies.size(); i++)
                if (otherConstituencies.get(i) != otherConstituency)
                  triple.getMetadata().put("extra" + i, constituencyToString(otherConstituencies.get(i)));
              triples.add(triple);
            }
          }
        }
      }
    }
    return triples;
  }
}

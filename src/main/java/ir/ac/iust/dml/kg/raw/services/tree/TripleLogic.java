package ir.ac.iust.dml.kg.raw.services.tree;

import com.google.common.collect.Lists;
import edu.stanford.nlp.ling.TaggedWord;
import ir.ac.iust.dml.kg.raw.Normalizer;
import ir.ac.iust.dml.kg.raw.POSTagger;
import ir.ac.iust.dml.kg.raw.SentenceTokenizer;
import ir.ac.iust.dml.kg.raw.WordTokenizer;
import ir.ac.iust.dml.kg.raw.rulebased.ExtractTriple;
import ir.ac.iust.dml.kg.raw.rulebased.Triple;
import ir.ac.iust.dml.kg.raw.services.access.TripleExtractor;
import ir.ac.iust.dml.kg.raw.services.access.entities.Occurrence;
import ir.ac.iust.dml.kg.raw.services.access.repositories.OccurrenceRepository;
import ir.ac.iust.dml.kg.raw.utils.ConfigReader;
import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Created by mohammad on 7/13/2017.
 */
@Service
public class TripleLogic {

    @Autowired
    private OccurrenceRepository dao;
    private final Log logger = LogFactory.getLog(getClass());

    public void writeTriplesToDb() throws IOException {
        File folder = new File("D:\\files");
        List<File> fileList = Arrays.asList(folder.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".txt"); // or something else
            }
        }));

        TripleExtractor tripleExtractor=new TripleExtractor();
        for (File file : fileList) {
            if (file.isFile()) {
                List<String> lines = FileUtils.readLines(file, "UTF-8");
                for (String line : lines) {
                    List<String> sentences = SentenceTokenizer.SentenceSplitterRaw(line);
                    for (String sentence : sentences) {
                        if (sentence.length() > 20 && sentence.length() < 200) {
                            try {
                               List<Triple> triples= tripleExtractor.extractTriplesByRules(sentence);

                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }
        }
    }

}

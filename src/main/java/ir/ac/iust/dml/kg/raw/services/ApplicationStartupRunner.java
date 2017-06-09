package ir.ac.iust.dml.kg.raw.services;

import ir.ac.iust.dml.kg.raw.services.tree.ParsingLogic;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
class ApplicationStartupRunner implements CommandLineRunner {

  private final Log logger = LogFactory.getLog(getClass());
  @Autowired
  private ParsingLogic parsingLogic;

  @Override
  public void run(String... args) throws Exception {
    logger.info("ApplicationStartupRunner run method Started !!");
    if (args.length == 0) return;
    if (args[0].equals("write")) parsingLogic.writeParses();
  }
}

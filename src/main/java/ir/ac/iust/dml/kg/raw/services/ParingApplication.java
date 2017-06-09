package ir.ac.iust.dml.kg.raw.services;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan
public class ParingApplication {

  public static void main(String[] args) {
    SpringApplication.run(ParingApplication.class, args);
  }
}

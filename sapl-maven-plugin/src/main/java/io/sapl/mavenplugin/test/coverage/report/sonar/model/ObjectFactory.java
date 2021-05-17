package io.sapl.mavenplugin.test.coverage.report.sonar.model;

import javax.xml.bind.annotation.XmlRegistry;


/**
 * This object contains factory methods for each 
 * Java content interface and Java element interface 
 * generated in the io.sapl.test.mavenplugin.model.sonar package. 
 * <p>An ObjectFactory allows you to programatically 
 * construct new instances of the Java representation 
 * for XML content. The Java representation of XML 
 * content can consist of schema derived interfaces 
 * and classes representing the binding of schema 
 * type definitions, element declarations and model 
 * groups.  Factory methods for each of these are 
 * provided in this class.
 * 
 */
@XmlRegistry
public class ObjectFactory {


    /**
     * Create a new ObjectFactory that can be used to create new instances of schema derived classes for package: io.sapl.test.mavenplugin.model.sonar
     * 
     */
    public ObjectFactory() {
    }

    /**
     * Create an instance of {@link Coverage }
     * 
     */
    public Coverage createCoverage() {
        return new Coverage();
    }

    /**
     * Create an instance of {@link Coverage.File }
     * 
     */
    public Coverage.File createCoverageFile() {
        return new Coverage.File();
    }

    /**
     * Create an instance of {@link Coverage.File.LineToCover }
     * 
     */
    public Coverage.File.LineToCover createCoverageFileLineToCover() {
        return new Coverage.File.LineToCover();
    }

}
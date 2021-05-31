package tools;

import Classification.OntologyReasoningService;
import ExceptionHandlers.ReasonerException;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

import java.io.*;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Set;

public abstract class InputSignatureHandler {
    private static String snomedIRIString = "http://snomed.info/id/";

    public static Set<OWLClass> extractRefsetClassesFromDescendents(OWLOntology inputOntology, OWLClass rootClass, boolean excludePrimitives) throws ReasonerException {
        OntologyReasoningService service = new OntologyReasoningService(inputOntology);
        service.classifyOntology();
        Set<OWLClass> conceptsInRefset = new HashSet<OWLClass>();

        conceptsInRefset.add(rootClass);
        for(OWLClass childCls:service.getDescendants(rootClass)) {
            if(!childCls.toString().equals("owl:Nothing")) {
                if (excludePrimitives) {
                    if (!service.isPrimitive(childCls)) {
                        System.out.println("Class: " + childCls);
                        conceptsInRefset.add(childCls);
                    }
                    continue;
                }
                conceptsInRefset.add(childCls);
            }
        }
        System.out.println("Total classes in refset: " + conceptsInRefset);

        return conceptsInRefset;
    }

    public static void printRefset(Set<OWLEntity> entitiesInRefset, String outputFilePath) throws IOException {
        Charset UTF_8_CHARSET = Charset.forName("UTF-8");
        BufferedWriter writer = new BufferedWriter (new OutputStreamWriter(new FileOutputStream(outputFilePath), UTF_8_CHARSET));
        for(OWLEntity ent:entitiesInRefset) {
            System.out.println("cls: " + ent.toString());
            String entID = ent.toString().replaceAll("[<>]", "");
            writer.write(entID.replace("http://snomed.info/id/",""));
            writer.newLine();
        }
        writer.close();
    }

    public static Set<OWLClass> readRefset(File refsetFile) {
        if(refsetFile.getName().substring(refsetFile.getName().lastIndexOf(".")).equals("json")) {
            return readRefsetJson(refsetFile);
        }
        return readRefsetTxt(refsetFile);
    }

    private static Set<OWLClass> readRefsetJson(File refsetFile) {
        OWLDataFactory df = OWLManager.createOWLOntologyManager().getOWLDataFactory();
        Set<OWLClass> classes = new HashSet<OWLClass>();
        try (BufferedReader br = new BufferedReader(new FileReader(refsetFile))) {
            String inLine = "";
            br.readLine();
            while ((inLine = br.readLine()) != null) {
                // process the line.
                System.out.println("Adding class: " + inLine + " to input");
                classes.add(df.getOWLClass(IRI.create(snomedIRIString + inLine)));
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return classes;
    }

    private static Set<OWLClass> readRefsetTxt(File refsetFile) {
        OWLDataFactory df = OWLManager.createOWLOntologyManager().getOWLDataFactory();
        Set<OWLClass> classes = new HashSet<OWLClass>();
        try (BufferedReader br = new BufferedReader(new FileReader(refsetFile))) {
            String inLine = "";
            br.readLine();
            while ((inLine = br.readLine()) != null) {
                // process the line, remove whitespace
                inLine = inLine.replaceAll("[\\s\\p{Z}]+", "").trim();
                System.out.println("Adding class: " + inLine + " to input");
                //if()
                classes.add(df.getOWLClass(IRI.create(snomedIRIString + inLine)));
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return classes;
    }

    public static Set<OWLClass> readClassesNonSCTFile(File signatureFile, String inputIRI) {
        OWLDataFactory df = OWLManager.createOWLOntologyManager().getOWLDataFactory();
        Set<OWLClass> classes = new HashSet<OWLClass>();
        try (BufferedReader br = new BufferedReader(new FileReader(signatureFile))) {
            String inLine = "";
            br.readLine();
            while ((inLine = br.readLine()) != null) {
                // process the line.
                System.out.println("Adding class: " + inLine + " to input");
                //if()
                classes.add(df.getOWLClass(IRI.create(inputIRI + inLine)));
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return classes;
    }

    public static void main(String[] args) throws OWLOntologyCreationException, ReasonerException, IOException {
        String inputPath = "E:/Users/warren/Documents/aPostdoc/SCT-files/";
        File inputOntologyFile = new File(inputPath + "sct-jan-2021.owl");

        OWLOntologyManager man = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = man.getOWLDataFactory();
        OWLOntology inputOntology = man.loadOntologyFromOntologyDocument(inputOntologyFile);
        String snomedIRIString = "http://snomed.info/id/";
        Set<OWLClass> conceptsToInclude = new HashSet<OWLClass>();
        conceptsToInclude.add(df.getOWLClass(IRI.create(snomedIRIString + "69896004")));
        conceptsToInclude.add(df.getOWLClass(IRI.create(snomedIRIString + "55464009")));
        conceptsToInclude.add(df.getOWLClass(IRI.create(snomedIRIString + "9631008")));

        Set<OWLClass> classes = new HashSet<OWLClass>();
        for(OWLClass cls:conceptsToInclude) {
            classes.addAll(InputSignatureHandler.extractRefsetClassesFromDescendents(inputOntology, cls, true));
        }

        String outputFilePath = "E:/Users/warren/Documents/aPostdoc/IAA-content-extraction/refsets/rheumatic-test/rheumatic_test_noPrimitives_refset.txt";
        InputSignatureHandler.printRefset(new HashSet<OWLEntity>(classes), outputFilePath);
    }
}

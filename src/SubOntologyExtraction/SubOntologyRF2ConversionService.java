package SubOntologyExtraction;

import ResultsWriters.RF2Printer;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.semanticweb.owlapi.model.*;
import org.snomed.otf.owltoolkit.conversion.ConversionException;
import org.snomed.otf.owltoolkit.conversion.OWLtoRF2Service;
import org.snomed.otf.owltoolkit.service.RF2ExtractionService;
import org.snomed.otf.owltoolkit.util.InputStreamSet;

import java.io.*;
import java.util.*;

/*
Converted to RF2 as follows, making use of the SNOMED OWL Toolkit RF2 conversion methods
Procedure is somewhat different, since subontology extraction utilises information from both the source ontology and the extracted subontology
        : All RF2 files except Relationships file - extracted from OWLtoRF2 of subontology
        : Concept RF2 - from source ontology, together with the signature of the subontology
        : Description RF2 - " "
        : Relationships RF2 - extracted from NNF file
        : OWLRefset RF2 - from subontology
        : Text Definitions RF2 - " "
 */
public class SubOntologyRF2ConversionService {

    private static String OWLRefsetRF2Directory = "authoring_OWLRefset_RF2";
    /*
    public SubOntologyRF2Converter(String outputPath, String backgroundFilePath) {
        this.outputPath = outputPath;
        this.backgroundFilePath = backgroundFilePath;
    }
     */

    //TODO: reduce redundancy in file extraction, automatically construct RF2 zip file
    public static void convertSubOntologytoRF2(OWLOntology subOntology, OWLOntology nnfOntology, String outputPath, String sourceFilePath) throws ReleaseImportException, ConversionException, IOException, OWLException {
        //Extract the concept and description RF2 files, based on the source ontology (includes all entities in subontology)
        Set<OWLEntity> entitiesInSubontologyAndNNFs = new HashSet<OWLEntity>();
        entitiesInSubontologyAndNNFs.addAll(subOntology.getClassesInSignature());
        entitiesInSubontologyAndNNFs.addAll(subOntology.getObjectPropertiesInSignature());
        entitiesInSubontologyAndNNFs.addAll(nnfOntology.getClassesInSignature());
        entitiesInSubontologyAndNNFs.addAll(nnfOntology.getObjectPropertiesInSignature());

        Set<OWLClass> inSubOntNotNNF = subOntology.getClassesInSignature();
        inSubOntNotNNF.removeAll(nnfOntology.getClassesInSignature());
        Set<OWLClass> inNNFNotSubOnt = nnfOntology.getClassesInSignature();
        inNNFNotSubOnt.removeAll(subOntology.getClassesInSignature());

        extractConceptAndDescriptionRF2(entitiesInSubontologyAndNNFs, outputPath, sourceFilePath);

        //Extract relationship rf2 file from nnfs
        printRelationshipRF2(nnfOntology, outputPath);

        //Extract OWLRefset rf2 file (and TextDefinitions file) from authoring definitions
        computeOWLRefsetAndTextDefinitions(outputPath);

        //merge RF2 components into single RF2.
        mergeContents(outputPath);

    }

    private static void extractConceptAndDescriptionRF2(Set<OWLEntity> entitiesToExtract, String outputPath, String backgroundFilePath) throws IOException, ReleaseImportException {
        String IRIPrefix = "http://snomed.info/id/";
        Set<Long> entityIDs = new HashSet<Long>();
        System.out.println("Extracting background RF2 information for entities in subontology.");
        System.out.println("Storing in " + outputPath + "subontologyRF2");
        for(OWLEntity ent:entitiesToExtract) {
            Long id = Long.parseLong(ent.toString().replaceFirst(IRIPrefix, "").replaceAll("[<>]", ""));
            entityIDs.add(id);
        }

        //TODO: add metadata concepts manually for now, improve later. -- needed?
        entityIDs.addAll(Arrays.asList(Long.parseLong("116680003"), Long.parseLong("410662002"), Long.parseLong("900000000000441003"), Long.parseLong("138875005")));

        new RF2ExtractionService().extractConcepts(
                new InputStreamSet(new File(backgroundFilePath)),
                entityIDs, new File(outputPath + "subontologyRF2"));

        //remove unnecessary OWLRefset file

    }

    private static void printRelationshipRF2(OWLOntology nnfOntology, String outputPath) throws IOException, ReleaseImportException, ConversionException {
        RF2Printer printer = new RF2Printer(outputPath);
        printer.printRelationshipRF2File(nnfOntology);
    }

    private static void computeOWLRefsetAndTextDefinitions(String outputPath) throws IOException, OWLException {
        OWLtoRF2Service owlToRF2Converter = new OWLtoRF2Service();
        InputStream isAuthoring = new FileInputStream(outputPath + "subOntology.owl");
        InputStream owlFileStreamAuthoring = new BufferedInputStream(isAuthoring);
        File rf2ZipAuthoring = new File(outputPath + OWLRefsetRF2Directory + ".zip");
        Date date = new Date();
        owlToRF2Converter.writeToRF2(owlFileStreamAuthoring, new FileOutputStream(rf2ZipAuthoring), date);
    }

    private static void mergeContents(String outputPath) {
        /*
        File zipFile = new File(outputPath + OWLRefsetRF2Directory + ".zip");
        try (FileSystem fileSystem = FileSystems.newFileSystem(zipFile, null)) {
            Path fileToExtract = fileSystem.getPath(fileName);
            Files.copy(fileToExtract, outputFile);
        }

         */
    }

        /*
    private static void printOWLRefsetRF2(OWLOntology subOntology, String outputPath) throws IOException, ReleaseImportException, ConversionException {
        RF2Printer printer = new RF2Printer(outputPath);
        printer.printOWLRefsetRF2File(subOntology);
    }
     */

}

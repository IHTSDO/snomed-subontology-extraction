package SubOntologyExtraction;

import ResultsWriters.OntologySaver;
import ResultsWriters.RF2Printer;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.snomed.otf.owltoolkit.conversion.ConversionException;
import org.snomed.otf.owltoolkit.conversion.OWLtoRF2Service;
import org.snomed.otf.owltoolkit.service.RF2ExtractionService;
import org.snomed.otf.owltoolkit.util.InputStreamSet;

import java.io.*;
import java.util.*;

/*
Converted to RF2 as follows
        : All RF2 files except Relationships file - extracted from OWLtoRF2 of subontology
        : Relationships RF2 file - extracted from NNF file (TODO: implement, use Axiom to Relationship conversion service as in RF2 printer)
 */
public class SubOntologyRF2Converter {

    private String outputPath;

    public SubOntologyRF2Converter(String outputPath) {
        this.outputPath = outputPath;
    }

    //TODO: reduce redundancy in file extraction, automatically construct RF2 zip file
    public void convertSubOntologytoRF2(OWLOntology subOntology, OWLOntology nnfOntology) throws OWLException, ReleaseImportException, ConversionException, IOException {
        Set<OWLEntity> entitiesInSubontologyAndNNFs = new HashSet<OWLEntity>();
        entitiesInSubontologyAndNNFs.addAll(subOntology.getClassesInSignature());
        entitiesInSubontologyAndNNFs.addAll(subOntology.getObjectPropertiesInSignature());
        entitiesInSubontologyAndNNFs.addAll(nnfOntology.getClassesInSignature());
        entitiesInSubontologyAndNNFs.addAll(nnfOntology.getObjectPropertiesInSignature());

        extractConceptAndTermInformationFromBackgroundRF2(entitiesInSubontologyAndNNFs);
        //Extract relationship rf2 file from nnfs
        printRelationshipRF2(nnfOntology, outputPath);

        //Extract refset rf2 file from authoring definitions
        OWLtoRF2Service owlToRF2Converter = new OWLtoRF2Service();
        InputStream isAuthoring = new FileInputStream(outputPath + "subOntology.owl");
        InputStream owlFileStreamAuthoring = new BufferedInputStream(isAuthoring);
        File rf2ZipAuthoring = new File(outputPath + "authoring_OWLRefset_RF2_" + new Date().getTime() + ".zip");
        owlToRF2Converter.writeToRF2(owlFileStreamAuthoring, new FileOutputStream(rf2ZipAuthoring), new GregorianCalendar(2020, Calendar.SEPTEMBER, 3).getTime());
    }

    private static void printRelationshipRF2(OWLOntology nnfOntology, String outputPath) throws IOException, ReleaseImportException, ConversionException {
        RF2Printer printer = new RF2Printer(outputPath);
        printer.printRelationshipRF2File(nnfOntology);
    }

    private void extractConceptAndTermInformationFromBackgroundRF2(Set<OWLEntity> entitiesToExtract) throws IOException, ReleaseImportException {
        //assumes SCT concepts, with standard IRI
        String IRIPrefix = "http://snomed.info/id/";
        Set<Long> entityIDs = new HashSet<Long>();
        System.out.println("Extracting background RF2 information for entities in subontology and inferred relationships.");
        System.out.println("Storing in " + outputPath + "conceptAndTermRF2");
        for(OWLEntity ent:entitiesToExtract) {
            Long id = Long.parseLong(ent.toString().replaceFirst(IRIPrefix, "").replaceAll("[<>]", ""));
            entityIDs.add(id);
        }

        //TODO: add metadata concepts manually for now, improve later.
        entityIDs.addAll(Arrays.asList(Long.parseLong("116680003"), Long.parseLong("410662002"), Long.parseLong("900000000000441003"), Long.parseLong("138875005")));

        new RF2ExtractionService().extractConcepts(
                new InputStreamSet(new File(outputPath + "SnomedCT_InternationalRF2_PRODUCTION_20200731T120000Z.zip")),
                entityIDs, new File(outputPath + "conceptAndTermRF2"));
    }

        /*
    private static void printOWLRefsetRF2(OWLOntology subOntology, String outputPath) throws IOException, ReleaseImportException, ConversionException {
        RF2Printer printer = new RF2Printer(outputPath);
        printer.printOWLRefsetRF2File(subOntology);
    }
     */

}

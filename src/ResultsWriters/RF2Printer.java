package ResultsWriters;

import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.snomed.otf.owltoolkit.constants.Concepts;
import org.snomed.otf.owltoolkit.conversion.AxiomRelationshipConversionService;
import org.snomed.otf.owltoolkit.conversion.ConversionException;
import org.snomed.otf.owltoolkit.domain.AxiomRepresentation;
import org.snomed.otf.owltoolkit.domain.Relationship;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipInputStream;

/*
* Prints OWLOntology statements as RF2 files. Based upon the RF2 services in the SNOMED OWL Toolkit.
* Key:
 */

public class RF2Printer extends Printer {

    private static String outputDirectory;
    private static final String TAB = "\t";
    private static final Charset UTF_8_CHARSET = Charset.forName("UTF-8");
    public static final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyyyMMdd");

    public RF2Printer(String outputPath){
        outputDirectory = outputPath;
    }

    public void printNNFsAsRF2Tuples(OWLOntology nnfOntology) throws ConversionException, IOException {
        //get attributes (roles) from ontology to be provided to toolkit OntologyService. TODO: we don't actually need the ontology service to go from OWL --> RF2, so doesn't matter?
        String outputFilePath = outputDirectory + "_FSN_tuples" + ".txt";

        System.out.println("Printing FSN tuples for definitions to: " + outputFilePath);
        AxiomRelationshipConversionService converter = new AxiomRelationshipConversionService(new HashSet<Long>());

        Map<Long, Set<OWLAxiom>> axiomsMap = new HashMap<Long, Set<OWLAxiom>>();

        axiomsMap.put((long) 1, nnfOntology.getAxioms());
        Map<Long, Set<AxiomRepresentation>> representationsMap = converter.convertAxiomsToRelationships(axiomsMap, false);

        Set<AxiomRepresentation> representations = representationsMap.get((long)1);

        BufferedWriter writer = new BufferedWriter (new OutputStreamWriter(new FileOutputStream(outputFilePath), UTF_8_CHARSET));
        StringBuilder sb = new StringBuilder();

        Map<Long, List<Relationship>> definedConceptRelationshipsMap = new HashMap<Long, List<Relationship>>();
        for(AxiomRepresentation rep:representations) {
            Long conceptID = rep.getLeftHandSideNamedConcept();
            Map<Integer, List<Relationship>> rightHandSideRelationshipsMap = rep.getRightHandSideRelationships();

            Map<Integer, List<Relationship>> rightHandSideRelationships = rep.getRightHandSideRelationships();
            for (Map.Entry<Integer, List<Relationship>> integerListEntry : rightHandSideRelationshipsMap.entrySet()) {
                List<Relationship> currentConceptRelationships = integerListEntry.getValue();
                for (Relationship rel : currentConceptRelationships) {
                    sb.append(conceptID);
                    sb.append("\t");
                    sb.append(rel.getTypeId());
                    sb.append("\t");
                    sb.append(rel.getDestinationId());
                    writer.write(sb.toString());
                    newline(writer);
                    sb.setLength(0);
                    writer.flush();
                }
            }
        }
    }

    //TODO: code duplication from above, reduce.
    public void printNNFsAsFSNTuples(OWLOntology nnfOntology) throws IOException, ConversionException {
        String outputFilePath = outputDirectory + "_FSN_tuples" + ".txt";

        AxiomRelationshipConversionService converter = new AxiomRelationshipConversionService(new HashSet<>());

        Map<Long, Set<OWLAxiom>> axiomsMap = new HashMap<>();
        axiomsMap.put((long) 1, nnfOntology.getTBoxAxioms(Imports.fromBoolean(false)));

        Map<Long, Set<AxiomRepresentation>> representationsMap = converter.convertAxiomsToRelationships(axiomsMap, false);
        Set<AxiomRepresentation> representations = representationsMap.get((long)1);

        //BufferedWriter writer =  new BufferedWriter(fw);
        BufferedWriter writer = new BufferedWriter (new OutputStreamWriter(new FileOutputStream(outputFilePath), UTF_8_CHARSET));

        Map<Long, String> conceptDescriptionMap = getConceptDescriptionMapFromDescriptionRF2();

        MapPrinter mapPrint = new MapPrinter("E:/Users/warren/Documents/aPostdoc");
        mapPrint.printGeneralMap(conceptDescriptionMap, "conceptDescriptionMap");

        int i=0;
        for(AxiomRepresentation rep:representations) {
            i++;
            System.out.println("Number printed: " + i);
            Long conceptID = rep.getLeftHandSideNamedConcept();
            Map<Integer, List<Relationship>> rightHandSideRelationshipsMap = rep.getRightHandSideRelationships();

            for (Map.Entry<Integer, List<Relationship>> integerListEntry : rightHandSideRelationshipsMap.entrySet()) {
                Map<Long, Set<Relationship>> definedConceptRelationshipsMap = new HashMap<>();//TODO: print for each concept, doesn't overwrite info then.
                List<Relationship> currentConceptRelationships = integerListEntry.getValue();
                definedConceptRelationshipsMap.put(conceptID, new HashSet<>(currentConceptRelationships));
                this.writeRelationshipChanges(writer, definedConceptRelationshipsMap, conceptDescriptionMap);
            }
        }
        System.out.println("NNF Ontology num axioms: " + nnfOntology.getAxiomCount());
        System.out.println("Axioms map size: "  + axiomsMap.size());
        System.out.println("Relationships map size: " + representationsMap.size());
        System.out.println("Representations size: " + representations.size());
    }

    //TODO: code reused from ClassificationResultsWriter in toolkit
    private void writeRelationshipChanges(
            BufferedWriter writer,
            Map<Long, Set<Relationship>> addedStatements,
            Map<Long, String> conceptDescriptionMap) throws IOException {

        // Write newly inferred relationships
        for (Long sourceId : addedStatements.keySet()) {
            String sourceTerm = conceptDescriptionMap.get(sourceId);
            System.out.println("Source term: " + sourceTerm + " for ID: " + sourceId);

            for (Relationship relationship : addedStatements.get(sourceId)) {
                Long destinationId = relationship.getDestinationId();
                String destinationTerm = conceptDescriptionMap.get(destinationId);

                Long relationshipTypeId = relationship.getTypeId();
                String relationshipTypeTerm = conceptDescriptionMap.get(relationshipTypeId);

                System.out.println("Destination term: " + destinationTerm + " for ID: " + destinationId);
                System.out.println("Relationship term: " + relationshipTypeTerm + " for ID: " + relationshipTypeId);

                writeRelationship(writer,
                        "added",
                        sourceTerm,
                        destinationTerm,
                        relationshipTypeTerm,
                        Concepts.EXISTENTIAL_RESTRICTION_MODIFIER);
                writer.flush();
            }
        }
        // Write redundant relationships
        /* TODO: currently no redundant relationships -- run is fresh each time with no delta.*/
    }

    private void writeRelationship(BufferedWriter writer, String status, String sourceTerm, String destinationTerm, String relationshipTypeTerm, String existentialRestrictionModifier) throws IOException {
        //add padding
        try {
            //writer.write(status);
            //writer.write(TAB);

            // sourceId
            writer.write(sourceTerm);
            writer.write(TAB);

            // typeId
            writer.write(relationshipTypeTerm);
            writer.write(TAB);

            // destinationId
            writer.write(destinationTerm);
            writer.write(TAB);

            writer.newLine();
        }
        catch(NullPointerException e) {
            //TODO: implement proper handling.
            System.out.println("Null exception.");
        }
    }

    public Map<Long, String> getConceptDescriptionMapFromDescriptionRF2() throws IOException {
        Map<Long, String> idToFSNMap = new HashMap<Long, String>();

        ClassLoader classloader = Thread.currentThread().getContextClassLoader();
        //InputStream is = classloader.getResourceAsStream("resources/sct2_Description_Snapshot-en_INT_20210131.zip");
        InputStream is = classloader.getResourceAsStream("resources/sct2_Description_roleChain_test.zip");
        ZipInputStream zipStream = new ZipInputStream(is);
        zipStream.getNextEntry();

        BufferedReader descriptionFileReader = new BufferedReader(new InputStreamReader(zipStream, StandardCharsets.UTF_8));//.lines().collect(Collectors.toList());

        for(String line:descriptionFileReader.lines().collect(Collectors.toList())) {
            String[] cols = line.split(TAB);
            if(cols[2].contains("1") && line.contains("900000000000003001")) {
                idToFSNMap.putIfAbsent(Long.parseLong(cols[4]), cols[7]);
            }
        }

        return idToFSNMap;
    }

    public void printRelationshipRF2File(OWLOntology nnfOntology) throws ConversionException, IOException {
        String date = SIMPLE_DATE_FORMAT.format(new Date().getTime());
        String outputFilePath = outputDirectory + "sct2_Relationship_Snapshot_INT" + date + ".txt";

        System.out.println("Writing inferred relationships file for: " + outputFilePath);
        AxiomRelationshipConversionService converter = new AxiomRelationshipConversionService(new HashSet<Long>());

        Map<Long, Set<OWLAxiom>> axiomsMap = new HashMap<Long, Set<OWLAxiom>>();

        axiomsMap.put((long) 1, nnfOntology.getAxioms());
        Map<Long, Set<AxiomRepresentation>> representationsMap = converter.convertAxiomsToRelationships(axiomsMap, false);

        Set<AxiomRepresentation> representations = representationsMap.get((long)1);

        BufferedWriter writer = new BufferedWriter (new OutputStreamWriter(new FileOutputStream(outputFilePath), UTF_8_CHARSET));
        StringBuilder sb = new StringBuilder();

        Map<Long, List<Relationship>> definedConceptRelationshipsMap = new HashMap<Long, List<Relationship>>();
        int i = 100;
        int check = 0;
        Random random = new Random();

        //print header
        sb.append("id\teffectiveTime\tactive\tmoduleId\tsourceId\tdestinationId\trelationshipGroup\ttypeId\tcharacteristicTypeId\tmodifierId");
        writer.write(sb.toString());
        newline(writer);
        sb.setLength(0);
        writer.flush();

        for(AxiomRepresentation rep:representations) {
            Long conceptID = rep.getLeftHandSideNamedConcept();
            Map<Integer, List<Relationship>> rightHandSideRelationshipsMap = rep.getRightHandSideRelationships();

            Iterator<Map.Entry<Integer, List<Relationship>>> iter = rightHandSideRelationshipsMap.entrySet().iterator();
            while(iter.hasNext()) {
                List<Relationship> currentConceptRelationships = iter.next().getValue();

                for (Relationship rel : currentConceptRelationships) {
                    //id - generate number XXX02X
                    i=i+1;
                    check = random.nextInt(10);//TODO: last digit should be checksum, implement
                    String id = i+"02"+check;
                    sb.append(id);
                    sb.append("\t");

                    //effectiveTime
                    String effectiveTime = "20201113";
                    sb.append(effectiveTime);
                    sb.append("\t");

                    //active
                    sb.append("1");
                    sb.append("\t");

                    //moduleId
                    sb.append("900000000000207008");
                    sb.append("\t");

                    //sourceId
                    sb.append(conceptID);
                    sb.append("\t");

                    //destinationId
                    sb.append(rel.getDestinationId());
                    sb.append("\t");

                    //relationshipGroup TODO: extract from nesting. Does getGroup do this sufficiently?
                    sb.append(rel.getGroup());
                    sb.append("\t");

                    //typeId
                    sb.append(rel.getTypeId());
                    sb.append("\t");

                    //charTypeId -- currently hardcoded + arbitrary
                    sb.append("900000000000011006");
                    sb.append("\t");

                    //modifierId -- currently hardcoded + arbitrary
                    sb.append("900000000000451002");
                    sb.append("\t");

                    writer.write(sb.toString());
                    newline(writer);
                    sb.setLength(0);
                    writer.flush();
                }
            }
        }
    }

    /*
    public void printOWLRefsetRF2File(OWLOntology authoringFormOntology) throws ConversionException, IOException {
        // Write OWL expression refset file
        String outputFilePath = outputDirectory + "OWLAxiom_Refset_RF2" + ".txt";
        BufferedWriter writer = new BufferedWriter (new OutputStreamWriter(new FileOutputStream(outputFilePath), UTF_8_CHARSET));
        StringBuilder sb = new StringBuilder();

        writer.write(RF2Headers.OWL_EXPRESSION_REFERENCE_SET_HEADER);
        newline(writer);
        for (Long conceptId : conceptAxioms.keySet()) {
            for (OWLAxiom owlAxiom : conceptAxioms.get(conceptId)) {
                // id	effectiveTime	active	moduleId	refsetId	referencedComponentId	owlExpression
                String axiomString = owlAxiom.toString();
                axiomString = axiomString.replace("<http://snomed.info/id/", ":");
                axiomString = axiomString.replace(">", "");
                writer.write(String.join("\t", UUID.randomUUID().toString(), "0", "1", Concepts.SNOMED_CT_CORE_MODULE,
                        Concepts.OWL_AXIOM_REFERENCE_SET, conceptId.toString(), axiomString));
                newline(writer);
            }
        }
        //needed metadata - TODO: clean
        writer.write(String.join("\t", UUID.randomUUID().toString(), "0", "1", Concepts.SNOMED_CT_CORE_MODULE, Concepts.OWL_AXIOM_REFERENCE_SET, "762705008", "SubClassOf(:762705008 :410662002)"));
        newline(writer);
        writer.write(String.join("\t", UUID.randomUUID().toString(), "0", "1", Concepts.SNOMED_CT_CORE_MODULE, Concepts.OWL_AXIOM_REFERENCE_SET, "410662002", "SubClassOf(:410662002 :900000000000441003)"));
        newline(writer);
        writer.write(String.join("\t", UUID.randomUUID().toString(), "0", "1", Concepts.SNOMED_CT_CORE_MODULE, Concepts.OWL_AXIOM_REFERENCE_SET, "900000000000441003", "SubClassOf(:900000000000441003 :138875005)"));
        newline(writer);
        writer.flush();

    }
     */
    public String getDirectoryPath() {
        return outputDirectory;
    }

}

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
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class RF2Printer extends Printer {

    private static String outputDirectory;
    private static final String TAB = "\t";
    private static final Charset UTF_8_CHARSET = Charset.forName("UTF-8");

    public RF2Printer(String outputPath){
        this.outputDirectory = outputPath;
    }

    //public void printNNFsAsRF2Tuples(Set<OWLAxiom> nnfAxioms) throws OWLOntologyCreationException, ConversionException, IOException {
    //    this.printNNFsAsRF2Tuples(OWLManager.createOWLOntologyManager().createOntology(nnfAxioms));
    //}

    public static void printNNFsAsRF2Tuples(OWLOntology nnfOntology) throws ConversionException, IOException {
        //get attributes (roles) from ontology to be provided to toolkit OntologyService. TODO: we don't actually need the ontology service to go from OWL --> RF2, so doesn't matter?
        String outputFilePath = outputDirectory + "_NNF_tuples" + ".txt";

        System.out.println("Printing FSN tuples for definitions to: " + outputFilePath);
        AxiomRelationshipConversionService converter = new AxiomRelationshipConversionService(new HashSet<Long>());

        Map<Long, Set<OWLAxiom>> axiomsMap = new HashMap<Long, Set<OWLAxiom>>();

        axiomsMap.put((long) 1, nnfOntology.getAxioms());
        Map<Long, Set<AxiomRepresentation>> representationsMap = converter.convertAxiomsToRelationships(axiomsMap, false);

        Set<AxiomRepresentation> representations = representationsMap.get((long)1);

        //FileWriter fw = new FileWriter(new File(outputFilePath));
        //BufferedWriter writer =  new BufferedWriter(fw);
        BufferedWriter writer = new BufferedWriter (new OutputStreamWriter(new FileOutputStream(outputFilePath), UTF_8_CHARSET));

        StringBuilder sb = new StringBuilder();

        Map<Long, List<Relationship>> definedConceptRelationshipsMap = new HashMap<Long, List<Relationship>>();
        for(AxiomRepresentation rep:representations) {
            Long conceptID = rep.getLeftHandSideNamedConcept();
            Map<Integer, List<Relationship>> rightHandSideRelationshipsMap = rep.getRightHandSideRelationships();

            Map<Integer, List<Relationship>> rightHandSideRelationships = rep.getRightHandSideRelationships();
            Iterator<Map.Entry<Integer, List<Relationship>>> iter = rightHandSideRelationshipsMap.entrySet().iterator();
            while(iter.hasNext()) {
                List<Relationship> currentConceptRelationships = iter.next().getValue();
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
        String outputFilePath = outputDirectory + "_NNF_tuples" + ".txt";
        //String outputFilePath = outputDirectory + "_NNF_tuples" + ".zip";

        AxiomRelationshipConversionService converter = new AxiomRelationshipConversionService(new HashSet<Long>());

        Map<Long, Set<OWLAxiom>> axiomsMap = new HashMap<Long, Set<OWLAxiom>>();
        axiomsMap.put((long) 1, nnfOntology.getTBoxAxioms(Imports.fromBoolean(false))); //TODO: getAxioms or getTBoxAxioms?

        System.out.println("AxiomsMap size: " + axiomsMap.entrySet());

        Map<Long, Set<AxiomRepresentation>> representationsMap = new HashMap<Long, Set<AxiomRepresentation>>();
        for(OWLAxiom ax:nnfOntology.getTBoxAxioms(Imports.fromBoolean(false))) {
            System.out.println("Ax string: " + ax.toString());
            AxiomRepresentation rep = converter.convertAxiomToRelationships(ax);
            if(rep == null) {
                System.out.println("Null representation for axiom: " + ax);
            }
        }

        //Map<Long, Set<AxiomRepresentation>> representationsMap = converter.convertAxiomsToRelationships(axiomsMap, false);
        Set<AxiomRepresentation> representations = representationsMap.get((long)1);
        System.out.println("Representations map keys: " + representationsMap.keySet());

        //System.out.println("representationsMap: " + representationsMap.toString());
        System.out.println("representations size: " + representations.size());

        FileWriter fw = new FileWriter(new File(outputFilePath));
        //BufferedWriter writer =  new BufferedWriter(fw);
        BufferedWriter writer = new BufferedWriter (new OutputStreamWriter(new FileOutputStream(outputFilePath), UTF_8_CHARSET));

        //OutputStream resultsOutputStream = new FileOutputStream(new File(outputFilePath));
        //ZipOutputStream zipOutputStream = new ZipOutputStream(resultsOutputStream, UTF_8_CHARSET);
        //BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(zipOutputStream));

        Map<Long, String> conceptDescriptionMap = getConceptDescriptionMapFromDescriptionRF2();

        int i=0;
        for(AxiomRepresentation rep:representations) {
            i++;
            System.out.println("Number printed: " + i);
            Long conceptID = rep.getLeftHandSideNamedConcept();
            Map<Integer, List<Relationship>> rightHandSideRelationshipsMap = rep.getRightHandSideRelationships();

            Iterator<Map.Entry<Integer, List<Relationship>>> iter = rightHandSideRelationshipsMap.entrySet().iterator();
            while(iter.hasNext()) {
                Map<Long, Set<Relationship>> definedConceptRelationshipsMap = new HashMap<Long, Set<Relationship>>();//TODO: print for each concept, doesn't overwrite info then.
                List<Relationship> currentConceptRelationships = iter.next().getValue();
                definedConceptRelationshipsMap.put(conceptID, new HashSet<Relationship>(currentConceptRelationships));
                this.writeRelationshipChanges(writer, definedConceptRelationshipsMap, conceptDescriptionMap);
            }
        }
    }

    //TODO: code reused from ClassificationResultsWriter in toolkit
    private void writeRelationshipChanges(
            BufferedWriter writer,
            Map<Long, Set<Relationship>> addedStatements,
            Map<Long, String> conceptDescriptionMap) throws IOException {

        // Write newly inferred relationships
        for (Long sourceId : addedStatements.keySet()) {
            String active = "1";
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
            writer.write(status);
            writer.write(TAB);

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
        InputStream is = classloader.getResourceAsStream("sct2_Description_Snapshot-en_INT_20200731.zip");
        ZipInputStream zipStream = new ZipInputStream(is);
        zipStream.getNextEntry();

        BufferedReader descriptionFileReader = new BufferedReader(new InputStreamReader(zipStream, StandardCharsets.UTF_8));//.lines().collect(Collectors.toList());

        Set<String> descriptionFileLines = new HashSet<String>();
        for(String line:descriptionFileReader.lines().collect(Collectors.toList())) {
            String[] cols = line.split(TAB);
            if(cols[2].contains("1") && line.contains("900000000000003001")) {
                idToFSNMap.putIfAbsent(Long.parseLong(cols[4]), cols[7]);
            }
        }

        return idToFSNMap;
    }

    public String getDirectoryPath() {
        return outputDirectory;
    }

}

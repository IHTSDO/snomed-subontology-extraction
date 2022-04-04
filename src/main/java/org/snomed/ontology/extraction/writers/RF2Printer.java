package org.snomed.ontology.extraction.writers;

import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.snomed.ontology.extraction.services.SubOntologyRF2ConversionService;
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

	private static final String TAB = "\t";
	private static final Charset UTF_8_CHARSET = StandardCharsets.UTF_8;
	public static final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyyyMMdd");
	private final File outputDirectory;

	public RF2Printer(File outputDirectory) {
		this.outputDirectory = outputDirectory;
	}

	public void printNNFsAsRF2Tuples(OWLOntology nnfOntology) throws ConversionException, IOException {
		//get attributes (roles) from ontology to be provided to toolkit OntologyService. TODO: we don't actually need the ontology service to go from OWL --> RF2, so doesn't matter.
		File outputFile = new File(outputDirectory, "_FSN_tuples" + ".txt");

		System.out.println("Printing FSN tuples for definitions to: " + outputFile.getPath());
		AxiomRelationshipConversionService converter = new AxiomRelationshipConversionService(new HashSet<>());

		Map<Long, List<OWLAxiom>> axiomsMap = new HashMap<>();

		axiomsMap.put((long) 1, new ArrayList<>(nnfOntology.getAxioms()));
		Map<Long, Set<AxiomRepresentation>> representationsMap = converter.convertAxiomsToRelationships(axiomsMap, false);

		Set<AxiomRepresentation> representations = representationsMap.get((long) 1);

		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFile), UTF_8_CHARSET));
		StringBuilder sb = new StringBuilder();

		Map<Long, List<Relationship>> definedConceptRelationshipsMap = new HashMap<Long, List<Relationship>>();
		for (AxiomRepresentation rep : representations) {
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
		File outputFile = new File(outputDirectory, "_FSN_tuples.txt");

		AxiomRelationshipConversionService converter = new AxiomRelationshipConversionService(new HashSet<>());

		Map<Long, List<OWLAxiom>> axiomsMap = new HashMap<>();
		axiomsMap.put((long) 1, new ArrayList<>(nnfOntology.getTBoxAxioms(Imports.fromBoolean(false))));

		Map<Long, Set<AxiomRepresentation>> representationsMap = converter.convertAxiomsToRelationships(axiomsMap, false);
		Set<AxiomRepresentation> representations = representationsMap.get((long) 1);

		//BufferedWriter writer =  new BufferedWriter(fw);
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFile), UTF_8_CHARSET));

		Map<Long, String> conceptDescriptionMap = getConceptDescriptionMapFromDescriptionRF2();

		MapPrinter mapPrint = new MapPrinter(new File("output"));
		mapPrint.printGeneralMap(conceptDescriptionMap, "conceptDescriptionMap");

		int i = 0;
		for (AxiomRepresentation rep : representations) {
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
		System.out.println("Axioms map size: " + axiomsMap.size());
		System.out.println("Relationships map size: " + representationsMap.size());
		System.out.println("Representations size: " + representations.size());
	}

	//code reused from ClassificationResultsWriter in IHTSDO owl toolkit
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
		/*currently no redundant relationships -- run is fresh each time with no delta.*/
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

			newline(writer);
		} catch (NullPointerException e) {
			System.out.println("Null exception.");
		}
	}

	public Map<Long, String> getConceptDescriptionMapFromDescriptionRF2() throws IOException {
		Map<Long, String> idToFSNMap = new HashMap<>();

		ClassLoader classloader = Thread.currentThread().getContextClassLoader();
		//InputStream is = classloader.getResourceAsStream("resources/sct2_Description_Snapshot-en_INT_20210131.zip");
		InputStream is = classloader.getResourceAsStream("resources/sct2_Description_roleChain_test.zip");
		ZipInputStream zipStream = new ZipInputStream(is);
		zipStream.getNextEntry();

		BufferedReader descriptionFileReader = new BufferedReader(new InputStreamReader(zipStream, StandardCharsets.UTF_8));//.lines().collect(Collectors.toList());

		for (String line : descriptionFileReader.lines().collect(Collectors.toList())) {
			String[] cols = line.split(TAB);
			if (cols[2].contains("1") && line.contains("900000000000003001")) {
				idToFSNMap.putIfAbsent(Long.parseLong(cols[4]), cols[7]);
			}
		}

		return idToFSNMap;
	}

	public void printRelationshipRF2Files(OWLOntology nnfOntology) throws ConversionException, IOException {
		AxiomRelationshipConversionService converter = new AxiomRelationshipConversionService(new HashSet<>());

		Set<AxiomRepresentation> representations = new HashSet<>();
		for (OWLAxiom axiom : nnfOntology.getAxioms()) {
			representations.add(converter.convertAxiomToRelationships(axiom));
		}

		File terminologyDirectory = new File(outputDirectory, "RF2/Snapshot/Terminology");
		terminologyDirectory.mkdirs();
		SCTIDSource relationshipIdSource = new SCTIDSource(SubOntologyRF2ConversionService.SCTID_GENERATION_NAMESPACE, "02", 100);

		File outputFile = new File(terminologyDirectory, "sct2_Relationship_Snapshot_INT_" + SIMPLE_DATE_FORMAT.format(new Date().getTime()) + ".txt");
		File concreteOutputFile = new File(terminologyDirectory, "sct2_RelationshipConcreteValues_Snapshot_INT_" + SIMPLE_DATE_FORMAT.format(new Date().getTime()) + ".txt");
		System.out.println("Writing inferred relationships files for: " + outputFile + " and " + concreteOutputFile);

		try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFile), UTF_8_CHARSET));
			 BufferedWriter concreteWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(concreteOutputFile), UTF_8_CHARSET))) {
			//print header
			writer.write("id\teffectiveTime\tactive\tmoduleId\tsourceId\tdestinationId\trelationshipGroup\ttypeId\tcharacteristicTypeId\tmodifierId");
			newline(writer);

			concreteWriter.write("id\teffectiveTime\tactive\tmoduleId\tsourceId\tvalue\trelationshipGroup\ttypeId\tcharacteristicTypeId\tmodifierId");
			newline(concreteWriter);

			BufferedWriter relationshipWriter;

			for (AxiomRepresentation rep : representations) {
				if (rep.getLeftHandSideNamedConcept().toString().contains("owl:Nothing")) { //TODO: 10-06-21 temp, nothing should not be included
					continue;
				}

				Long conceptID = rep.getLeftHandSideNamedConcept();
				for (List<Relationship> relationshipGroup : rep.getRightHandSideRelationships().values()) {
					for (Relationship rel : relationshipGroup) {

						relationshipWriter = rel.isConcrete() ? concreteWriter : writer;

						//id - generate number XXX02X
						relationshipWriter.write(relationshipIdSource.getNewId());
						relationshipWriter.write("\t");

						//effectiveTime - blank
						relationshipWriter.write("\t");

						//active
						relationshipWriter.write("1");
						relationshipWriter.write("\t");

						//moduleId
						relationshipWriter.write("900000000000207008");
						relationshipWriter.write("\t");

						//sourceId
						relationshipWriter.write(conceptID.toString());
						relationshipWriter.write("\t");

						//destinationId
						if (rel.isConcrete()) {
							relationshipWriter.write(rel.getValue().getRF2Value());
						} else {
							relationshipWriter.write(Long.toString(rel.getDestinationId()));
						}
						relationshipWriter.write("\t");

						//relationshipGroup TODO: extract from nesting. Does getGroup do this sufficiently?
						relationshipWriter.write(Integer.toString(rel.getGroup()));
						relationshipWriter.write("\t");

						//typeId
						relationshipWriter.write(Long.toString(rel.getTypeId()));
						relationshipWriter.write("\t");

						//charTypeId -- currently hardcoded + arbitrary
						relationshipWriter.write("900000000000011006");
						relationshipWriter.write("\t");

						//modifierId -- currently hardcoded + arbitrary
						relationshipWriter.write("900000000000451002");

						newline(relationshipWriter);
					}
				}
			}
		}
	}

}

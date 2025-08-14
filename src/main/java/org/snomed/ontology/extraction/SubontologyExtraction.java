package org.snomed.ontology.extraction;

import com.google.common.collect.Lists;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLException;
import org.semanticweb.owlapi.model.OWLOntology;
import org.snomed.ontology.extraction.definitiongeneration.RedundancyOptions;
import org.snomed.ontology.extraction.exception.ReasonerException;
import org.snomed.ontology.extraction.services.SubOntologyExtractionHandler;
import org.snomed.ontology.extraction.services.SubOntologyRF2ConversionService;
import org.snomed.ontology.extraction.tools.InputSignatureHandler;
import org.snomed.ontology.extraction.utils.MainMethodUtils;
import org.snomed.ontology.extraction.verification.VerificationChecker;
import org.snomed.ontology.extraction.writers.MapPrinter;
import org.snomed.ontology.extraction.writers.OntologySaver;
import org.snomed.otf.owltoolkit.conversion.ConversionException;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.snomed.ontology.extraction.utils.MainMethodUtils.*;

public class SubontologyExtraction {

	private static final String ARG_HELP = "-help";
	private static final String ARG_SOURCE_ONTOLOGY_FILE = "-source-ontology";
	private static final String ARG_INPUT_SUBSET = "-input-subset";
	private static final String ARG_OUTPUT_RF2 = "-output-rf2";
	private static final String ARG_OUTPUT_PATH = "-output-path";
	private static final String ARG_RF2_SNAPSHOT_ARCHIVE = "-rf2-snapshot-archive";
	private static final String ARG_VERIFY_SUBONTOLOGY = "-verify-subontology";

	public static void main(String[] argsArray) {
		try {
			new SubontologyExtraction().run(argsArray);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
		System.exit(0);
	}

	public void run(String[] argsArray) throws OWLException, ReasonerException, IOException, ReleaseImportException, ConversionException {
		List<String> args = Lists.newArrayList(argsArray);

		MainMethodUtils.setPrintHelp(this::printHelp);
		if (args.isEmpty() || args.contains(ARG_HELP)) {
			// Help
			printHelp();
			System.exit(0);
		}

		final File sourceOntologyFile = getFile(getRequiredParameterValue(ARG_SOURCE_ONTOLOGY_FILE, args));

		boolean outputRF2 = isFlag(ARG_OUTPUT_RF2, args);
		//if computing RF2, provide RF2 files corresponding to the sourceOntologyFile OWL file for OWL to RF2 conversion -- ensure same ontology version as sourceOntologyFile
		File sourceRF2File = null;
		if(outputRF2) {
			sourceRF2File =  getFile(getRequiredParameterValue(ARG_RF2_SNAPSHOT_ARCHIVE, args));
		}

		/*
		* Input for subontology extraction: source ontology (path), inputRefsetFile for focus concepts (list, refset as .txt)
		 */
		File inputRefsetFile = getFile(getRequiredParameterValue(ARG_INPUT_SUBSET, args));
		// if focus concepts specified as refset
		Set<OWLClass> conceptsToDefine;
		if (outputRF2 && sourceRF2File != null) {
			// Use enhanced parsing that supports "<<" flag for descendants when RF2 archive is available
			conceptsToDefine = InputSignatureHandler.readRefsetWithDescendants(inputRefsetFile, sourceRF2File);
		} else {
			// Use standard parsing when no RF2 archive is provided
			conceptsToDefine = InputSignatureHandler.readRefset(inputRefsetFile);
		}

		// alternatively, can specify concepts directly as a set e.g.
		/*
		Set<OWLClass> conceptsToDefine = new HashSet<OWLClass>();
		OWLDataFactory df = man.getOWLDataFactory();
		conceptsToDefine.add(df.getOWLClass(IRI.create("http://snomed.info/id/" + 22688005)));
		 */

		boolean verifySubontology = isFlag(ARG_VERIFY_SUBONTOLOGY, args);

		//output path
		final File outputDirectory = new File(getParameterValue(ARG_OUTPUT_PATH, args, "output"));
		if (!outputDirectory.isDirectory() && !outputDirectory.mkdirs()) {
			System.err.println("Failed to create output directory: " + outputDirectory.getAbsolutePath());
		}

		OWLOntology sourceOntology = OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(sourceOntologyFile);
		//generating subontology
		SubOntologyExtractionHandler generator = new SubOntologyExtractionHandler(sourceOntology, conceptsToDefine);

		////////
		//REDUNDANCY ELIMINATION OPTIONS
		////////
		//redundancy elimination options -- optional, if not specified, default will be used
		Set<RedundancyOptions> customRedundancyOptions = new HashSet<>();

		//AUTHORING FORM -- recommended: leave as default (false). These axioms will appear as stated axioms in the subontology. Subontology behaviour not tested with custom "authoring forms".
		//			   -- "false": applies redundancy options only to nnf definitions (inferred relationship file)
		//			   -- "true": applies to both the authoring forms (stated axioms in subontology) and nnfs
		boolean defaultAuthoringForm = true;

		//Example of specifying non-default redundancy elimination options.
		/*
		Set<RedundancyOptions> customRedundancyOptions = new HashSet<RedundancyOptions>();
		customRedundancyOptions.add(RedundancyOptions.eliminateLessSpecificRedundancy);
		customRedundancyOptions.add(RedundancyOptions.eliminateRoleGroupRedundancy);
		customRedundancyOptions.add(RedundancyOptions.eliminateSufficientProximalGCIs);
		 */

		long startTime = System.currentTimeMillis();
		if(customRedundancyOptions.isEmpty()) { //with default redundancy elimination on both authoring and NNF definitions (RECOMMENDED)
			generator.computeSubontology(outputRF2);
		} else if(defaultAuthoringForm) { //default authoring form, custom nnf
			generator.computeSubontology(outputRF2, customRedundancyOptions);
		} else { //custom authoring and nnf (NOT RECOMMENDED)
			//with non-default redundancy elimination options specified by user
			generator.computeSubontology(outputRF2, customRedundancyOptions, defaultAuthoringForm);
		}

		long endTime = System.currentTimeMillis();
		OWLOntology subOntology = generator.getCurrentSubOntology();
		OntologySaver.saveOntology(subOntology, outputDirectory, "subOntology.owl");
		System.out.println("Time taken: " + (endTime - startTime)/1000 + " seconds");
		//Extract RF2 for subontology
		if(outputRF2) {
			//generator.generateNNFs();
			OWLOntology nnfOntology = generator.getNnfOntology();
			OntologySaver.saveOntology(nnfOntology, outputDirectory, "subOntologyNNFs.owl");

			SubOntologyRF2ConversionService.convertSubOntologytoRF2(subOntology, nnfOntology, outputDirectory, sourceRF2File);
		}
		if(verifySubontology) {
			VerificationChecker checker = new VerificationChecker();
			System.out.println("==========================");
			System.out.println("VERIFICATION: Step (1) (defined) focus concept equivalence");
			System.out.println("==========================");
			boolean satisfiesEquivalentFocusConceptsRequirement = checker.namedFocusConceptsSatisfyEquivalence(generator.getFocusConcepts(), subOntology, sourceOntology);
			System.out.println("Satisfies equivalence of (defined) focus classes requirement?" + satisfiesEquivalentFocusConceptsRequirement);

			System.out.println("==========================");
			System.out.println("VERIFICATION: Step (2) transitive closure equal within sig(subOntology)");
			System.out.println("==========================");
			boolean satisfiesTransitiveClosureReq = checker.satisfiesTransitiveClosureRequirement(subOntology, sourceOntology);
			System.out.println("Satisfies transitive closure requirement?" + satisfiesTransitiveClosureReq);

			if (!satisfiesEquivalentFocusConceptsRequirement || !satisfiesTransitiveClosureReq) {
				if(!satisfiesEquivalentFocusConceptsRequirement) {
					System.out.println("Equivalence test failed.");
					Set<OWLClass> failedCases = checker.getFailedFocusClassEquivalenceCases();
					System.out.println("Failed cases for equivalence: ");
					System.out.println(failedCases);
					System.out.println("Num failed cases: " + failedCases.size() + " out of: " + conceptsToDefine.size());
				}
				if(!satisfiesTransitiveClosureReq) {
					System.out.println("Transitive Closure test failed");
					System.out.println("Failed classes: " + checker.getLatestSourceOntologyClosureDiffs().keySet());
					MapPrinter printer = new MapPrinter(outputDirectory);
					printer.printGeneralMap(checker.getLatestSubOntologyClosureDiffs(), "subOntDiffMap.txt");
					printer.printGeneralMap(checker.getLatestSourceOntologyClosureDiffs(), "sourceOntDiffMap.txt");
				}
			}
			else {
				System.out.println("org.snomed.ontology.extraction.Verification passed.");
				System.out.println("Input ontology num axioms: " + sourceOntology.getLogicalAxiomCount());
				System.out.println("Input ontology num classes: " + sourceOntology.getClassesInSignature().size() + ", " +
						"object properties: " + sourceOntology.getObjectPropertiesInSignature().size() + " and data properties: " + sourceOntology.getDataPropertiesInSignature().size());
				System.out.println("Subontology Stats");
				System.out.println("Num axioms: " + subOntology.getLogicalAxiomCount());
				System.out.println("Num classes: " + subOntology.getClassesInSignature().size() + ", " +
						"object properties: " + subOntology.getObjectPropertiesInSignature().size() + " and data properties: " + subOntology.getDataPropertiesInSignature().size());
				System.out.println("Focus classes: " + conceptsToDefine.size());
				System.out.println("Supporting classes: " + (subOntology.getClassesInSignature().size() - conceptsToDefine.size()));
				System.out.println("---------------------");
				System.out.println("Other stats: ");
				System.out.println("Number of definitions added for supporting classes: " + generator.getNumberOfAdditionalSupportingClassDefinitionsAdded());
				System.out.println("Num supporting classes added by incremental signature expansion: " + generator.getNumberOfClassesAddedDuringSignatureExpansion());
				System.out.println("Supporting classes with incrementally added definitions: " + generator.getSupportingClassesWithAddedDefinitions().toString());
			}
		}
	}

	private void printHelp() {
		System.out.println(
				"Usage:\n" +
						pad(ARG_HELP) +
						"Print this help message.\n" +
						"\n" +

						pad(ARG_SOURCE_ONTOLOGY_FILE) +
						"Source ontology OWL file.\n" +
						pad("") + "This can be generated using the snomed-owl-toolkit project.\n" +
						"\n" +

						pad(ARG_INPUT_SUBSET) +
						"Input subset file.\n" +
						pad("") + "Text file containing a newline separated list of identifiers of the SNOMED-CT concepts to extract into the subontology.\n" +
						pad("") + "When using " + ARG_OUTPUT_RF2 + ", the file can also contain concept IDs with a \"<<\" flag to include all descendants.\n" +
						pad("") + "Example:\n" +
						pad("") + "  131148009 |Bleeding (finding)|\n" +
						pad("") + "  <<239161005 |Wound hemorrhage (finding)|\n" +
						pad("") + "  16541001 |Yellow fever (disorder)|\n" +
						"\n" +

						"\n" +
						"\n" +
						"Optional parameters for OWL conversion:\n" +

						pad(ARG_OUTPUT_RF2) +
						"(Optional) This flag enables RF2 output.\n" +
						pad("") + "If this flag is given then an RF2 snapshot to filter is required as input using " + ARG_RF2_SNAPSHOT_ARCHIVE + ".\n" +
						"\n" +

						pad(ARG_OUTPUT_PATH) +
						"(Optional) Output directory path. Defaults to 'output'.\n" +
						"\n" +

						pad(ARG_RF2_SNAPSHOT_ARCHIVE) +
						"This parameter is required when using " + ARG_OUTPUT_RF2 + ".\n" +
						pad("") + "A SNOMED CT RF2 archive containing a snapshot. The release version must match the source ontology OWL file.\n" +
						"\n" +

						pad(ARG_VERIFY_SUBONTOLOGY) +
						"(Optional) runs verification for the computed subontology to check steps 1 and 2 above.\n" +
						pad("") + "Warning: this can be expensive for larger subontologies.\n" +
						"\n" +

						"");
	}

}

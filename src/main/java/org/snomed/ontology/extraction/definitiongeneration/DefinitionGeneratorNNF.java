package org.snomed.ontology.extraction.definitiongeneration;

import org.snomed.ontology.extraction.classification.OntologyReasoningService;
import org.snomed.ontology.extraction.naming.IntroducedNameHandler;
import org.semanticweb.owlapi.model.*;

import java.util.*;

public class DefinitionGeneratorNNF extends DefinitionGenerator {

	public DefinitionGeneratorNNF(OWLOntology inputOntology, OntologyReasoningService reasonerService, IntroducedNameHandler namer) {
		super(inputOntology, reasonerService, namer);
	}

	public void generateDefinition(OWLClass inputClass) {
		//default: all redundancy elimination
		Set<RedundancyOptions> defaultOptions = new HashSet<>();
		defaultOptions.add(RedundancyOptions.eliminateLessSpecificRedundancy);
		defaultOptions.add(RedundancyOptions.eliminateReflexivePVRedundancy);
		defaultOptions.add(RedundancyOptions.eliminateRoleGroupRedundancy);
		this.generateDefinition(inputClass, defaultOptions);
	}

	public void generateDefinition(OWLClass inputClass, Set<RedundancyOptions> redundancyOptions) {
		//Need set of ancestors, split into classes and PVs, excluding all introduced names
		Set<OWLClass> ancestors = reasonerService.getAncestors(inputClass);
		Set<OWLClass> ancestorRenamedPVs = extractNamedPVs(ancestors);

		System.out.println("Computing NNF for INPUT CLASS: " + inputClass);

		Set<OWLClass> parentNamedClasses = new HashSet<>(reasonerService.getDirectAncestors(inputClass));

		//remove all introduced classes to name PVs and name GCIs
		parentNamedClasses.removeAll(ancestorRenamedPVs);

		//ensure renamed GCIs (e.g. "GCI_0") are being ignored here.
		List<OWLClass> parentsToCheckForGCIs = new ArrayList<>(extractNamedGCIs(parentNamedClasses));
		ListIterator<OWLClass> iterator = parentsToCheckForGCIs.listIterator();
		while(iterator.hasNext()) {
			OWLClass parentToCheck = iterator.next();
			if(namer.isNamedGCI(parentToCheck)) {
				parentNamedClasses.remove(parentToCheck);
				for(OWLClass nextParent:reasonerService.getDirectAncestors(parentToCheck)) {
					if(!namer.isNamedPV(nextParent)) {
						parentNamedClasses.add(nextParent);
						iterator.add(nextParent);
						iterator.previous();
					}
				}
			}
		}

		Set<OWLClass> reducedParentNamedClasses;
		Set<OWLRestriction> reducedAncestorPVs;

		if(redundancyOptions.contains(RedundancyOptions.eliminateLessSpecificRedundancy)) {
			reducedParentNamedClasses = reduceClassSet(parentNamedClasses);
			reducedAncestorPVs = replaceNamesWithPVs(reduceClassSet(ancestorRenamedPVs));
		}
		else {
			reducedParentNamedClasses = parentNamedClasses;
			reducedAncestorPVs = replaceNamesWithPVs(ancestorRenamedPVs);
		}
		if(redundancyOptions.contains(RedundancyOptions.eliminateRoleGroupRedundancy)) {
			reducedAncestorPVs = eliminateRoleGroupRedundancies(reducedAncestorPVs);
		}
		if(redundancyOptions.contains(RedundancyOptions.eliminateReflexivePVRedundancy)) {
			reducedAncestorPVs = eliminateReflexivePVRedundancies(inputClass, reducedAncestorPVs);
		}

		Set<OWLClassExpression> nonRedundantAncestors = new HashSet<>();
		nonRedundantAncestors.addAll(reducedParentNamedClasses);
		nonRedundantAncestors.addAll(reducedAncestorPVs);

		Map<Set<OWLClassExpression>, Boolean> nonRedundantAncestorsMap = new HashMap<>();
		nonRedundantAncestorsMap.put(nonRedundantAncestors, false);
		constructDefinition(inputClass, nonRedundantAncestorsMap);
	}
}

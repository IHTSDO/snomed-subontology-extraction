package org.snomed.ontology.extraction.definitiongeneration;

import org.snomed.ontology.extraction.classification.OntologyReasoningService;
import org.snomed.ontology.extraction.naming.IntroducedNameHandler;
import org.semanticweb.owlapi.model.*;

import java.util.*;

public class DefinitionGeneratorAbstract extends DefinitionGenerator {

	public DefinitionGeneratorAbstract(OWLOntology inputOntology, OntologyReasoningService reasonerService, IntroducedNameHandler namer) {
		super(inputOntology, reasonerService, namer);
	}

	public void generateDefinition(OWLClass inputClass) {
		//default: all redundancy removed
		Set<RedundancyOptions> defaultOptions = new HashSet<>();
		defaultOptions.add(RedundancyOptions.eliminateLessSpecificRedundancy);
		defaultOptions.add(RedundancyOptions.eliminateReflexivePVRedundancy);
		defaultOptions.add(RedundancyOptions.eliminateRoleGroupRedundancy);
		this.generateDefinition(inputClass, defaultOptions);
	}

	//multi-axiom case -- key part here, must obtain closest classes (i.e. in def) then ancestors *of those classes*, then reduce.
	//				 -- this way, the ancestors associated with each individual *axiom* are kept together.
	public void generateDefinition(OWLClass classToDefine, Set<RedundancyOptions> redundancyOptions) {
		//separate authoring form for GCIs: do not want to inherit PVs and ancestors from "above", i.e., from necessary conditions.
		if (namer.isNamedGCI(classToDefine)) {
			computeAuthoringFormForGCI(classToDefine);
			return;
		}

		//if class has multiple definitional axioms (i.e. == and ==, or == and <=), handle each separately.
		Set<OWLAxiom> sourceDefinitionsForClass = new HashSet<>();
		sourceDefinitionsForClass.addAll(sourceOntology.getEquivalentClassesAxioms(classToDefine));
		sourceDefinitionsForClass.addAll(sourceOntology.getSubClassAxiomsForSubClass(classToDefine));

		Map<Set<OWLClassExpression>, Boolean> definingConditionsForEachAxiom = new HashMap<>(); //better type than map?
		for (OWLAxiom sourceAx : sourceDefinitionsForClass) {
			boolean isEquivalence = sourceAx.getAxiomType().equals(AxiomType.EQUIVALENT_CLASSES);
			Set<OWLClass> statedDirectParents = new HashSet<>();
			//Set<OWLClass> closestParentPVs = new HashSet<OWLClass>();

			//gather the stated direct parents of the class to be defined
			if (sourceAx instanceof OWLSubClassOfAxiom) {
				Set<OWLClassExpression> closestParentExpressions = ((OWLSubClassOfAxiom) sourceAx).getSuperClass().asConjunctSet();
				for (OWLClassExpression exp : closestParentExpressions) {
					//should only be classes and PVs (R some or RG some)
					if (exp instanceof OWLClass) {
						statedDirectParents.add((OWLClass) exp);
					} else if (exp instanceof OWLObjectSomeValuesFrom) {
						statedDirectParents.add(namer.retrieveNameForPV((OWLObjectSomeValuesFrom) exp));
					} else if (exp instanceof OWLDataHasValue) {
						statedDirectParents.add(namer.retrieveNameForPV((OWLDataHasValue) exp));
					}
				}
			} else if (sourceAx instanceof OWLEquivalentClassesAxiom) {
				Set<OWLSubClassOfAxiom> axs = ((OWLEquivalentClassesAxiom) sourceAx).asOWLSubClassOfAxioms();
				for (OWLSubClassOfAxiom ax : axs) {
					if (ax.getSubClass().equals(classToDefine)) {
						Set<OWLClassExpression> closestParentsExpressions = ax.getSuperClass().asConjunctSet();
						for (OWLClassExpression exp : closestParentsExpressions) {
							if (exp instanceof OWLClass) {
								statedDirectParents.add((OWLClass) exp);
							} else if (exp instanceof OWLObjectSomeValuesFrom) {
								statedDirectParents.add(namer.retrieveNameForPV((OWLObjectSomeValuesFrom) exp));
							} else if (exp instanceof OWLDataHasValue) {
								statedDirectParents.add(namer.retrieveNameForPV((OWLDataHasValue) exp));
							}
						}
					}
				}
			}

			//calculate ancestors of stated parents, + store all. Store "proximal" primitives (prior to reduction).
			Set<OWLClass> ancestors = new HashSet<>(statedDirectParents);
			Set<OWLClass> closestPrimitives = new HashSet<>();
			for (OWLClass parent : statedDirectParents) {
				ancestors.addAll(reasonerService.getAncestors(parent));
				if (!namer.isNamedPV(parent) && reasonerService.isPrimitive(parent)) {
					closestPrimitives.add(parent);
				} else {
					closestPrimitives.addAll(computeClosestPrimitiveAncestors(parent));
				}
			}

			Set<OWLClass> ancestorRenamedPVs = extractNamedPVs(ancestors);

			closestPrimitives.removeAll(ancestorRenamedPVs);
			closestPrimitives.removeAll(extractNamedGCIs(closestPrimitives));

			Set<OWLClass> reducedParentNamedClasses;
			Set<OWLRestriction> reducedAncestorPVs;

			//TODO: check, if this form of redundancy was non-optional, could skip the reduction step entirely? Would be more efficient.
			if (redundancyOptions.contains(RedundancyOptions.eliminateLessSpecificRedundancy)) {
				reducedParentNamedClasses = reduceClassSet(closestPrimitives);
				System.out.println("Parents before GCI check: " + reducedParentNamedClasses);

				boolean gciParentsChanged = false;
				Set<OWLClass> removedParents = new HashSet<>();
				if (redundancyOptions.contains(RedundancyOptions.eliminateSufficientProximalGCIs)) {
					System.out.println("Eliminating sufficient proximal GCI concepts");
					Set<OWLClass> parentsAfterCheckingGCIs = eliminateSufficientProximalGCIConcepts(classToDefine, reducedParentNamedClasses);

					removedParents.addAll(reducedParentNamedClasses);
					removedParents.removeAll(parentsAfterCheckingGCIs);

					parentsAfterCheckingGCIs = reduceClassSet(parentsAfterCheckingGCIs);

					if (!parentsAfterCheckingGCIs.equals(reducedParentNamedClasses)) {
						gciParentsChanged = true;
						reducedParentNamedClasses = parentsAfterCheckingGCIs;
					}
				}
				//if parents changed, then eliminate PVs inherited from type 1 gci concepts.
				if (gciParentsChanged) {
					System.out.println("GCI parents changed for class: " + classToDefine);
					Set<OWLClass> pvsToCheck = new HashSet<>(ancestorRenamedPVs);

					for (OWLClass pv : pvsToCheck) {
						//boolean pvInheritedFromTypeOneGCI = true;
						boolean pvInheritedFromTypeOneGCI = false;
						for (OWLClass parent : reducedParentNamedClasses) {
							//if an ancestor of a retained parent, or a direct ancestor of the class being defined, keep.
							if (removedParents.contains(parent)) {
								pvInheritedFromTypeOneGCI = true;
								break;
							}
						}
						if (pvInheritedFromTypeOneGCI) {
							ancestorRenamedPVs.remove(pv);
						}
					}
				}
				reducedAncestorPVs = replaceNamesWithPVs(reduceClassSet(ancestorRenamedPVs));
			} else {
				reducedParentNamedClasses = closestPrimitives;
				reducedAncestorPVs = replaceNamesWithPVs(ancestorRenamedPVs);
			}
			if (redundancyOptions.contains(RedundancyOptions.eliminateRoleGroupRedundancy)) {
				reducedAncestorPVs = eliminateRoleGroupRedundancies(reducedAncestorPVs);
			}
			if (redundancyOptions.contains(RedundancyOptions.eliminateReflexivePVRedundancy)) {
				reducedAncestorPVs = eliminateReflexivePVRedundancies(classToDefine, reducedAncestorPVs);
			}

			Set<OWLClassExpression> nonRedundantAncestors = new HashSet<>();
			nonRedundantAncestors.addAll(reducedParentNamedClasses);
			nonRedundantAncestors.addAll(reducedAncestorPVs);

			definingConditionsForEachAxiom.put(nonRedundantAncestors, isEquivalence);
		}
		constructDefinition(classToDefine, definingConditionsForEachAxiom);
	}

	//possibly quicker than taking all primitive ancestors & redundancy checking?
	private Set<OWLClass> computeClosestPrimitiveAncestors(OWLClass classToDefine) {
		List<OWLClass> currentClassesToExpand = new ArrayList<>();
		Set<OWLClass> closestPrimitives = new HashSet<>();

		currentClassesToExpand.add(classToDefine);

		ListIterator<OWLClass> iterator = currentClassesToExpand.listIterator();
		while (iterator.hasNext()) {
			OWLClass cls = iterator.next();
			Set<OWLClass> parentClasses = reasonerService.getDirectAncestors(cls);
			Set<OWLClass> namedPVs = extractNamedPVs(parentClasses);
			parentClasses.removeAll(namedPVs);
			for (OWLClass parent : parentClasses) {
				//If is primitive, add to returned set
				if (reasonerService.isPrimitive(parent)) {
					closestPrimitives.add(parent);
					continue;
				}
				//If not primitive, add to check
				iterator.add(parent);
				iterator.previous();
			}
		}

		return closestPrimitives;
	}

	//type 1 and type 2 GCI subconcepts:
	//	  *type 1: classToDefine is subconcept of a sufficiency condition of a GCI concept. Here: replace GCI concept with next proximal primitives
	//	  *type 2: classToDefine is subconcept of a necessary condition of a GCI concept. Here: normal authoring form, no change.
	private Set<OWLClass> eliminateSufficientProximalGCIConcepts(OWLClass classToDefine, Set<OWLClass> parentClasses) {
		Set<OWLClass> newProximalPrimitiveParents = new HashSet<>();
		ListIterator<OWLClass> parentIterator = new ArrayList<>(parentClasses).listIterator();

		//for(OWLClass parent:parentClasses) {
		while (parentIterator.hasNext()) {
			OWLClass parent = parentIterator.next();
			//for each parent A, check if occurs in axiom of form C <= A, where C is a complex concept
			if (namer.hasAssociatedGCIs(parent)) {
				System.out.println("Found GCI parent: " + parent + "Checking type of subconcept relationship for class: " + classToDefine);
				//for each associated GCI, check type 1 or type 2 relationship for classToDefine
				boolean isTypeOne = false;
				for (OWLClass gciName : namer.returnNamesOfGCIsForSuperConcept(parent)) {
					if (reasonerService.getAncestors(classToDefine).contains(gciName)) {
						//type 1 -- add proximal primitives of GCI concept to parents for classToDefine, replacing GCI concept.
						System.out.println("Type 1 concept detected: " + classToDefine + " with parent: " + parent);
						isTypeOne = true;
						Set<OWLClass> gciProximalPrimitives = computeClosestPrimitiveAncestors(parent);
						System.out.println("Prox primitive parents for GCI concept: " + parent + " are: " + gciProximalPrimitives);
						for (OWLClass proximalPrimitive : gciProximalPrimitives) {
							//newProximalPrimitiveParents.add(proximalPrimitive);
							parentIterator.add(proximalPrimitive);
							parentIterator.previous();
						}
						break;
					}
				}
				//type 2 -- retain GCI concept in proximal primitive parent set
				if (!isTypeOne) {
					System.out.println("Type 2 concept detected: " + classToDefine + " with parent: " + parent);
					newProximalPrimitiveParents.add(parent);
					continue;
				}
			} else {
				//if not GCI concept, retain proximal primitive parent
				newProximalPrimitiveParents.add(parent);
			}
		}
		return newProximalPrimitiveParents;
	}

	private void computeAuthoringFormForGCI(OWLClass gciName) {
		//TODO: improve.
		//Given axiom of the form B and R some C <= A, where "A" is the GCI concept, have name GCI_A == B and R some C.
		//Process is then to compute the authoring form "up to the sufficient condition".

		//start with original GCI
		//Set<OWLClass> parentsOfGCI = reasonerService.getDirectAncestors(gciName);
		//parentsOfGCI.remove(namer.retrieveSuperClassFromNamedGCI(gciName));
		System.out.println("COMPUTING AUTHORING FORM FOR GCI: " + gciName);
		OWLClassExpression originalGCI = namer.retrieveExpressionFromGCIName(gciName);

		Set<OWLClass> conceptsInOriginalGCI = new HashSet<>();
		Set<OWLClassExpression> pvsInOriginalGCI = new HashSet<>();
		for (OWLClassExpression exp : originalGCI.asConjunctSet()) {
			if (exp instanceof OWLClass) {
				conceptsInOriginalGCI.add((OWLClass) exp);
				continue;
			}
			pvsInOriginalGCI.add(exp);
		}

		Set<OWLClassExpression> authoringFormCandidates = new HashSet<>();

		//replace any occurrences of defined (named) concepts via equivalent replacement
		for (OWLClass cls : conceptsInOriginalGCI) {
			if (!reasonerService.isPrimitive(cls)) {
				generateDefinition(cls);
				System.out.println("Definition generated for cls: " + cls);
				System.out.println("getting latest necessary conditions: " + getLatestNecessaryConditions());
				authoringFormCandidates.addAll(getLatestNecessaryConditions());
				continue;
			}
			authoringFormCandidates.add(cls);
		}

		authoringFormCandidates.addAll(pvsInOriginalGCI);

		System.out.println("authoring form candidates: " + authoringFormCandidates);

		//reduce the sets
		Set<OWLClass> conceptsInAuthoringForm = new HashSet<>();
		Set<OWLClass> pvNamesInAuthoringForm = new HashSet<>();
		for (OWLClassExpression exp : authoringFormCandidates) {
			if (exp instanceof OWLClass) {
				conceptsInAuthoringForm.add((OWLClass) exp);
				continue;
			}
			pvNamesInAuthoringForm.add(namer.retrieveNameForPV((OWLRestriction) exp));
		}

		System.out.println("Concepts in auth form: " + conceptsInAuthoringForm);
		System.out.println("Pvs in auth form: " + pvNamesInAuthoringForm);

		conceptsInAuthoringForm = reduceClassSet(conceptsInAuthoringForm);
		pvNamesInAuthoringForm = reduceClassSet(pvNamesInAuthoringForm);

		Set<OWLClassExpression> authoringForm = new HashSet<>(conceptsInAuthoringForm);

		for (OWLClass pvName : pvNamesInAuthoringForm) {
			authoringForm.add(namer.retrievePVForName(pvName));
		}

		System.out.println("Authoring form parents for class: " + gciName + " are: " + authoringForm);

		Map<Set<OWLClassExpression>, Boolean> authoringFormSet = new HashMap<>();

		authoringFormSet.put(authoringForm, false);
		constructDefinition(gciName, authoringFormSet);
	}

}

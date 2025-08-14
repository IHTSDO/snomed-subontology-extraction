package org.snomed.ontology.extraction.services;

import org.ihtsdo.otf.snomedboot.factory.ImpotentComponentFactory;
import org.snomed.ontology.extraction.tools.InputSignatureHandler.ConceptInfo;

import java.util.function.Consumer;

public class ConceptInfoComponentFactory extends ImpotentComponentFactory {

	private final Consumer<ConceptInfo> conceptInfoConsumer;

	public ConceptInfoComponentFactory(Consumer<ConceptInfo> conceptInfoConsumer) {
		this.conceptInfoConsumer = conceptInfoConsumer;
	}

	@Override
	public void newConceptState(String conceptId, String effectiveTime, String active, String moduleId, String definitionStatusId) {
		conceptInfoConsumer.accept(new ConceptInfo(Long.parseLong(conceptId), active, moduleId));
	}
}

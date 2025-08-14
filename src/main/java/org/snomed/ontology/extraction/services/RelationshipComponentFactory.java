package org.snomed.ontology.extraction.services;

import it.unimi.dsi.fastutil.Pair;
import org.ihtsdo.otf.snomedboot.factory.ImpotentComponentFactory;
import org.snomed.otf.owltoolkit.constants.Concepts;

import java.util.function.Consumer;

public class RelationshipComponentFactory extends ImpotentComponentFactory {

	private final Consumer<Pair<Long, Long>> parentChildPairConsumer;

	public RelationshipComponentFactory(Consumer<Pair<Long, Long>> parentChildPairConsumer) {
		this.parentChildPairConsumer = parentChildPairConsumer;
	}

	@Override
	public void newRelationshipState(String id, String effectiveTime, String active, String moduleId, String sourceId, String destinationId, String relationshipGroup, String typeId, String characteristicTypeId, String modifierId) {
		if ("1".equals(active) && Concepts.IS_A.equals(typeId) && Concepts.INFERRED_RELATIONSHIP.equals(characteristicTypeId)) {
			// Pass parent (destinationId) and child (sourceId).
			parentChildPairConsumer.accept(Pair.of(Long.parseLong(destinationId), Long.parseLong(sourceId)));
		}
	}
}

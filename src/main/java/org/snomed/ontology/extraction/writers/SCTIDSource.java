package org.snomed.ontology.extraction.writers;

public class SCTIDSource {

	private final String namespaceId;
	private final String partitionId;
	private long identifierOffset;

	public SCTIDSource(int namespaceId, String partitionId, long identifierOffset) {
		this.namespaceId = String.valueOf(namespaceId);
		this.partitionId = partitionId;
		this.identifierOffset = identifierOffset;
	}

	public String getNewId() {
		String id = String.format("%s%s%s", ++identifierOffset, namespaceId, partitionId);
		return id + VerhoeffCheck.calculateChecksum(id, false);
	}

}

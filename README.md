# SNOMED CT Subontology Extraction Prototype

Produces an extracted subontology for a given background ontology and set of concepts, with the aim of satisfying the 
following criteria:
- All focus (input) concept definitions are equivalent in the source and sub ontologies.
- The transitive closure between all concepts in the final subontology is equal (up to the signature of the subontology)
  in the source and sub ontologies.

**Working Group**: Warren Del-Pinto (warren.del-pinto@manchester.ac.uk), Renate A. Schmidt (renate.schmidt@manchester.ac.uk), Yongsheng Gao (SNOMED International), Kai Kewley (SNOMED International)

### Process outline
Steps:
1) Compute abstract (authoring) form definitions for each focus concept in the input set (including GCI axioms for these concepts)
2) Definition expansion: automatically identify required supporting concept definitions to satisfy the above criteria
3) Populate RBox (currently star module of roles appearing in the definitions added during steps (1) and (2)
4) Addition of top-level SCT groupers (later may expand to utilise grouper selection algorithms)
5) Completion of the transitive closure between all concepts in the subontology, using atomic inclusions A1 <= A2 only where necessary
6) Shrinking of subontology hierarchy (i.e., removal of unnecessary supporting concepts)

When computing the subontology in RF2 format, the following steps are also included:
- Automatic addition of required metadata concepts (for loading into subontology browser, e.g. language concepts)
- Computation of NNF definitions for Relationship RF2 file

## Running the prototype

The application can be run from the command line using the 'executable' jar file available on the [latest release](https://github.com/IHTSDO/snomed-subontology-extraction/releases) page.

Command line options:
```
Usage:
 -help                                  Print this help message.

 -source-ontology                       Source ontology OWL file.
                                        This can be generated using the snomed-owl-toolkit project.

 -input-subset                          Input subset file.
                                        Text file containing a newline separated list of identifiers of the SNOMED-CT concepts to extract into the subontology.



Optional parameters for OWL conversion:
 -output-rf2                            (Optional) This flag enables RF2 output.
                                        If this flag is given then an RF2 snapshot to filter is required as input using -rf2-snapshot-archive.

 -rf2-snapshot-archive                  This parameter is required when using -output-rf2.
                                        A SNOMED CT RF2 archive containing a snapshot. The release version must match the source ontology OWL file.

 -verify-subontology                    (Optional) runs verification for the computed subontology to check steps 1 and 2 above.
                                        Warning: this can be expensive for larger subontologies.

```

### Example command line options
```
java -Xms4g -jar snomed-subontology-extraction-1.0.0-executable.jar \
 -source-ontology ../release/snomed-int-20200731-ontology.owl \
 -input-subset concept-ids-from-dentistry-refset.txt \
 -output-rf2 \
 -rf2-snapshot-archive ../release/SnomedCT_InternationalRF2_PRODUCTION_20200731T120000Z.zip 
```

## Compiling RF2 notes
Currently RF2 files are printed as follows:
- **subontologyRF2 folder** contains all required RF2 files aside from the following...
- **Relationship RF2 file** placed inside same directory as above folder, to compile RF2 move this to 
  Snapshot/Terminology subdirectory in subontologyRF2
- **authoring_OWLRefset_RF2** two files should be copied from this folder to the Snapshot/Terminology subdirectory of 
  the subontologyRF2 folder:
  - _**sct2_sRefset_OWLExpressionSnapshot.txt**_ : contains the OWL definitions associated with the subontology
  - _**sct2_TextDefinition_Snapshot.txt**_: contains the associated descriptions etc required for the browser

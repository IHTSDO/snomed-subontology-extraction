# SNOMED CT Subontology Extraction Prototype

Produces an extracted subontology for a given background ontology and set of concepts, with the aim of satisfying the 
following criteria:
- All focus (input) concept definitions are equivalent in the source and sub ontologies.
- The transitive closure between all concepts in the final subontology is equal (up to the signature of the subontology)
  in the source and sub ontologies.

**Working Group**: Warren Del-Pinto (warren.del-pinto@manchester.ac.uk), Renate A. Schmidt (renate.schmidt@manchester.ac.uk), Yongsheng Gao (SNOMED International), [Kai Kewley](https://github.com/kaicode) (SNOMED International)

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
                                        When using -output-rf2, the file can also contain concept IDs with a "<<" flag to include all descendants.
                                        Example:
                                          131148009
                                          <<239161005
                                          16541001



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
java -Xms4g -jar snomed-subontology-extraction-*-executable.jar \
 -source-ontology ../release/snomed-int-20200731-ontology.owl \
 -input-subset concept-ids-from-dentistry-refset.txt \
 -output-rf2 \
 -rf2-snapshot-archive ../release/SnomedCT_InternationalRF2_PRODUCTION_20200731T120000Z.zip 
```

### RF2 Output
All RF2 files are written to "output/RF2" ready to be zipped into an RF2 snapshot archive.   
**Please note**: Inferred relationship records have throw-away generated identifiers in a demo namespace that must not be relied upon.

### Subset Parsing with Descendants
When using the `-output-rf2` flag, the subset file can include concept IDs with a `<<` flag to automatically include all descendants of those concepts. This is useful for including entire hierarchies in your subontology.

**Format:**
- Regular concept IDs: `131148009` or `131148009 |Bleeding (finding)|`
- Concepts with descendants: `<<239161005` or `<<239161005 |Wound hemorrhage (finding)|`

**Example subset file:**
```
131148009 |Bleeding (finding)|
<<239161005 |Wound hemorrhage (finding)|
16541001 |Yellow fever (disorder)|
```

This will include:
- Concept 131148009 directly
- Concept 239161005 and all its descendants (found using the RF2 inferred hierarchy)
- Concept 16541001 directly

The tool will use the inferred hierarchy from the RF2 snapshot archive to identify all descendants of any flagged concepts.

**Note:** Concept terms (e.g., `|Bleeding (finding)|`) are optional and are ignored during parsing. They are useful for documentation purposes to make subset files more readable.

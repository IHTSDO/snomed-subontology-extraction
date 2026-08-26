# Changelog

## 2.2.2

### Fixes
- Write additional refsets to the `Refsets/Content/` directory, not `Refsets/`

## 2.2.1

### Fixes
- Generated inferred relationships use the subontology module (`31000003106`) instead of the International Release Module

## 2.2.0

### Features
- `-include-inactive` flag to include inactive concepts in RF2 output
- `-effective-time` option to set the effective time on generated RF2 output (`yyyyMMdd`)
- RF2 output includes refsets listed in the input subset (axiom and language refsets remain included by default)
- Associated concepts referenced by included refset rows are added recursively
- Module Dependency Refset (MDRS) generated for the subontology module
- Test subontology module concept (`31000003106`) always included in RF2 output
- Module concepts automatically collected and included in RF2 output
- Ancestors of metadata concepts included in extraction
- SNOMED CT license `Readme.txt` included in RF2 output directory

### Improvements
- Refactored RF2 processing to reduce read/write passes
- Refset rows filtered to only those relevant to the extracted subset
- Effective time populated on all generated RF2 rows
- README expanded with RF2 output, refset, module, and inactive concept documentation

### Fixes
- MRCM refset extraction writes to the correct subdirectory with all fields
- Null check added to prevent runtime errors during RF2 processing

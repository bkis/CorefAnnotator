[![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.1228106.svg)](https://doi.org/10.5281/zenodo.1228106)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
![Github All Releases](https://img.shields.io/github/downloads/nilsreiter/CorefAnnotator/total.svg)
![GitHub (pre-)release](https://img.shields.io/github/release/nilsreiter/CorefAnnotator/all.svg)
[![Build Status](https://travis-ci.org/nilsreiter/CorefAnnotator.svg?branch=master)](https://travis-ci.org/nilsreiter/CorefAnnotator)
[![CodeFactor](https://www.codefactor.io/repository/github/nilsreiter/corefannotator/badge)](https://www.codefactor.io/repository/github/nilsreiter/corefannotator) [![Join the chat at https://gitter.im/CorefAnnotator/community](https://badges.gitter.im/CorefAnnotator/community.svg)](https://gitter.im/CorefAnnotator/community?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
# Coref Annotator

This is an annotation tool for coreference. It's built on top of [Apache's UIMA](https://uima.apache.org), and works with long documents and long coreference chains.

## Features

- Supports annotation of long texts with many discourse entities
- Mentions can be non-continuous
- Intuitive drag and drop operations
- Fully operable by keyboard for fast annotation
- Annotation texts can be formatted
- Search function to navigate in long texts
- Localisable in other languages (currently: English and German)
- Import and export in a few file formats, including Excel for easy analysis
- Automatically generated candidates
- Search terms (including regular expressions) can be annotated en bloc
- Visualization and some simple quantitative analysis of the annotations

## Requirements and Installation

See file [INSTALL.md](INSTALL.md)
 
## How to cite
If you are using this annotation tool, it would be nice to cite this 
publication:

> Nils Reiter. **CorefAnnotator - A New Annotation Tool for Entity References**. In Abstracts of EADH: Data in the Digital Humanities, December 2018.
DOI: 10.18419/opus-10144

```bibtex
@inproceedings{ Reiter2018ag,
   Title = {{CorefAnnotator - A New Annotation Tool for Entity References}},
   Author = { Nils Reiter },
   Booktitle = {{Abstracts of EADH: Data in the Digital Humanities}},
   Location = { Galway, Ireland },
   Month = { December },
   Doi = { 10.18419/opus-10144 },
   Year = { 2018 }
}
```


## Screenshots

### Main Window

![Main Window (v1.0.0-SNAPSHOT)](src/main/resources/docs/screenshots/screen0.png)

### Annotation Window

![Annotation Window (v1.0.0-SNAPSHOT)](src/main/resources/docs/screenshots/screen1.png)

![Annotation Window with formatted text (v1.9.1)](src/main/resources/docs/screenshots/screen2.png)



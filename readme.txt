Missing Tests
-------------
This project contains the accompanying files for the paper "A Method for Finding Missing Tests"


Contents:
=========
Missing-Test-Plugin:
--IntelliJ project that contains the plugin
reports.json:
--The results of running the plugin on the subjects. A description of the schema can be found in analyzer
analyzer:
-- IntelliJ project that contains Kotlin functions for analyzing the reports.json file
csv
--output directory for the analyzer to dump csv files
jacoco
--jacoco coverage reports for each of the subjects

To Run Plugin:
==============
Open Missing-Test-Plugin as an IntelliJ project. When run, the plugin will launch a new instance of IntelliJ, from which you will need to load your target project. Then find the Plugin dropdown on the top menu and select Run Plugin

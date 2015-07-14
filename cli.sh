#!/bin/sh
mvn compile exec:java -Dexec.mainClass="de.uds.lsv.platon.ui.PlatonCli" -Dexec.args="$*"

**All the projects in this repository require JDK8 or greater**

#UCS implementation using NiFi#

This is the home repository for the UCS implementation using NiFi workflow processor (https://nifi.incubator.apache.org/).

## How to run UCS server ##

### 1.- NiFi Server ###
First thing we need in order to run UCS-NiFi server is a running instance of NiFi.

You can either get the binaries from here https://nifi.incubator.apache.org/downloads, or build it yourself following the instructions here:  https://nifi.incubator.apache.org/development/quickstart.html

Before you start NiFi server, please complete the following steps.

### 2.- Cognitive's NiFi Extensions ###
UCS-NiFi uses a set of common processors that are placed in this repository: https://bitbucket.org/cogmedsys/vacds-nifi-extensions

You need to clone this repository and build it. Then, you need to copy the file **$VACDS_NIFI_EXTENSIONS_HOME/nifi-cognitive-extensions/nifi-cognitive-nar/target/nifi-cognitive-nar-0.1-SNAPSHOT.nar** into NiFi's directory **$NIFI_HOME/lib**

### 3.- Build this project ###
This repository contains a NiFi-based implementation of UCS. After you build all the projects in this repository, you need to copy the generated file **$VACDS_UCS_NIFI/ucs-nifi-extensions/nifi-ucs-nifi-extensions-nar/target/nifi-ucs-nifi-extensions-nar-0.1-SNAPSHOT.nar** into NiFi's directiory **$NIFI_HOME/lib**

### 4.- Run NiFi ###
Before start NiFi server, we need to replace the configuration file **$NIFI_HOME/conf/controller-services.xml** with the configuration files that corresponds to the template we want to use in step #5 (i.e. ./ucs-nifi-extensions/nifi-ucs-nifi-extensions-processors/src/test/resources/nifi-templates/v3/controller-services.xml).

From NiFis **bin** directory, run the command *./nifi.sh start*

### 5.- Import UCS template into NiFi ###
From within NiFi's UI, import the template you want to use from **$VACDS_UCS_NIFI/ucs-nifi-extensions/nifi-ucs-nifi-extensions-processors/src/test/resources/nifi-templates/vXXX/ucs-vXXX.xml** (i.e. **$VACDS_UCS_NIFI/ucs-nifi-extensions/nifi-ucs-nifi-extensions-processors/src/test/resources/nifi-templates/v3/ucs-v3.xml**).
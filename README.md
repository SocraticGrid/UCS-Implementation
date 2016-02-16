**All the projects in this repository require JDK8 or greater**

# UCS implementation using NiFi

This is the home repository for the UCS implementation using NiFi workflow processor (https://nifi.incubator.apache.org/).

[![Build](https://img.shields.io/shippable/56c2dfb41895ca4474741acc.svg)](https://app.shippable.com/projects/56c2dfb41895ca4474741acc)
[![License](https://img.shields.io/badge/license-apache%202.0-ff69b4.svg)](http://www.apache.org/licenses/LICENSE-2.0)

## How to run UCS server

### 1.- NiFi Server

The first thing we need in order to run UCS-NiFi server is to setup a [NiFi](https://nifi.apache.org/) instance.

You can either get the binaries from [here](https://nifi.incubator.apache.org/downloads), 
or build it yourself by following the instructions [here](https://nifi.apache.org/quickstart.html).

The current implementation of UCS was tested with NiFi version 0.4.1.

Before you start NiFi server, please complete the following steps.


### 2.- Cognitive's NiFi Extensions

UCS-NiFi uses a set of common processors that are placed in the [nifi-extensions repository](https://bitbucket.org/cogmedsys/nifi-extensions).

You need to clone this repository and build it. Then, you need to copy the file 
`$NIFI_EXTENSIONS_HOME/nifi-cognitive-extensions/nifi-cognitive-nar/target/nifi-cognitive-nar-0.2-SNAPSHOT.nar` 
into NiFi's directory `$NIFI_HOME/lib`.

A pre-built version of the common processors can be found as part of the 
[ucs-docker repository](https://bitbucket.org/cogmedsys/ucs-docker/raw/a3b789d0d8864eb2ce7542aba7ed8fa8a977a9f0/ucs-nifi-docker/nifi-cognitive-nar-0.2-SNAPSHOT.nar).


### 3.- Build this project

This repository contains a NiFi-based implementation of UCS. After you build all 
the projects in this repository, you need to copy the generated file 
`$UCS_IMPLEMENTATION_HOME/ucs-nifi-extensions/nifi-ucs-nifi-extensions-nar/target/nifi-ucs-nifi-extensions-nar-0.4-SNAPSHOT.nar` 
into NiFi's directiory `$NIFI_HOME/lib`

A pre-built version of the UCS-related processors can be found as part of the 
[ucs-docker repository](https://bitbucket.org/cogmedsys/ucs-docker/raw/a3b789d0d8864eb2ce7542aba7ed8fa8a977a9f0/ucs-nifi-docker/nifi-ucs-nifi-extensions-nar-0.4-SNAPSHOT.nar).


### 4.- Configure NiFi Flow

Before start NiFi server, we need to replace the flow it will run (with the 
UCS flow).

Different versions of the UCS flow can be found in 
[ucs-nifi-extensions/nifi-ucs-nifi-extensions-processors](/ucs-nifi-extensions/nifi-ucs-nifi-extensions-processors/src/test/resources/nifi-templates)
module. Starting from **v29**, these templates can be used in version 0.4.1 of NiFi.
Previous versions of the templates are outdated and they target older versions of NiFi.

In order to tell NiFi which flow we want to run, we have to select the biggest
version from the `/nifi-templates` directory and copy the `flow.xml.gz` file
it contains into `$NIFI_HOME/conf/`.


### 5.- (Optional) Configure NiFi's Port

By default, NiFi will run on port 8080. If, for some reason, we need to change the
port number we want to use, we have to edit NiFi's configuration file: `$NIFI_HOME/conf/nifi.properties`.
The property we should change inside this file is *nifi.web.http.port*.

This configuration file contains many other configuration options that we may
want to review in case something else needs to be configured.


### 6.- Import UCS template into NiFi

From NiFi's `bin` directory, run the command `./nifi.sh start`.

Once NiFi is running, you can access its we console using the following URL: [http://localhost:8080/nifi](http://localhost:8080/nifi).



# UCS-NIFI-API

## Running Integration Tests

The integration tests in this project all extend from 
`org.socraticgrid.hl7.ucs.nifi.integration.BaseIntegrationTest`.
By default, these tests are not executed. If you want to execute them, you need to
enable Maven's "it" profile. For example:

`mvn clean install -Pit`

These tests expect a running instance of UCS somewhere in the network. The easiest
way to run them is by using UCS' docker image:

`docker run -it --rm -p 6060:8080 -p 8888-8892:8888-8892  cognitivemedicine/ucs:0.5`

The tests require 2 configuration options:

* The host where UCS is running: By default, this value is "localhost"
* The host where the tests are running: By default, this value is "172.17.0.1".

If you want to change the default values of these configuration options, you can
use the following System Properties or Environment Variables:

* ucs.nifi.api.nifi.host
* ucs.nifi.api.client.host

For example,

`mvn clean install -Pit -Ducs.nifi.api.nifi.host=192.168.0.3 -Ducs.nifi.api.client.host=localhost`

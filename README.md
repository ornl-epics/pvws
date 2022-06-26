PV Web Socket
=============

Web Socket for PVs.

Combines ideas from https://github.com/JeffersonLab/epics2web
with https://github.com/xihui/WebPDA:

 * Web Socket for EPICS Channel Access and PV Access, simulated PVs, local PVs, formulas ... based on Phoebus core-pv and RxJava
 * Scalar and array values
 * Basic values plus time stamps, status/severity, metadata for units, display ranges etc
 * Generally using JSON to simplify use in JavaScript web client, but packing array values as binary to reduce size
 * Example JavaScript client library, but usable by any web client
 * Metadata is sent once with first value, then only when it changes to reduce network traffic.
   Example JavaScript client merges updates to always present the complete value with all metadata

Key user is [Display Builder Web Runtime](https://github.com/ornl-epics/dbwr).


Change Log
----------

Version information is displayed at the bottom of the built-in web page,
see `<div id="versions">` in
https://github.com/ornl-epics/pvws/blob/main/src/main/webapp/index.html


Building
--------

To build with maven:

    mvn clean package

Project can also be imported into Eclipse JEE IDE
via File, Import, Maven, Existing Maven Projects.

This builds against a released version of core-pv and jca. To use the "latest" build from locally compiled versions of for example https://github.com/epics-base/jca.git and https://github.com/ControlSystemStudio/phoebus/tree/master/core/pva, mvn install these, then update the pom.xml to list their 1.2.3-SNAPSHOT versions, which should use the binaries that you just installed locally.


Running under Tomcat
--------------------

Set the following environment variables, for example in `$CATALINA_HOME/bin/setenv.sh` or `tomcat.conf`, depending on the version and installation details:

Channel Access Settings:
 * `EPICS_CA_ADDR_LIST`: CA address list.
 * `EPICS_CA_MAX_ARRAY_BYTES`: CA array size.

PV Access Settings:
 * `EPICS_PVA_ADDR_LIST`: Space-separated list of host names or IP addresses. Each may be followed by ":port", otherwise defaulting to EPICS_PVA_BROADCAST_PORT. When empty, local subnet is used.
 * `EPICS_PVA_AUTO_ADDR_LIST`: 'YES' (default) or 'NO'.
 * `EPICS_PVA_BROADCAST_PORT`: Port used for name searches, defaults to 5076.
 * `EPICS_PVA_NAME_SERVERS`: Space-separated list of TCP name servers, provided as IP address followed by optional ":port". Client will connect to each address and send name searches before using the EPICS_PVA_ADDR_LIST for UDP searches. Set EPICS_PVA_ADDR_LIST to empty and EPICS_PVA_AUTO_ADDR_LIST=NO to use only the TCP name servers and avoid all UDP traffic.

Web Socket Settings:
 * `PV_THROTTLE_MS`: Throttle-latest period in milliseconds (default: 1000).
 * `PV_ARRAY_THROTTLE_MS`: .. for arrays (default: 10000).
 * `PV_WRITE_SUPPORT`: Set to `true` to enable writing (default: false).


 
Place `pvws.war` in `$CATALINA_HOME/webapps`.
You can check the tomcat log for the effective values
since they will be logged when the context starts up.

When enabling write access, actual write access is still controlled
on a per-PV basis by Channel Access or PV Access security,
but note that the user and host seen by the CA resp. PVA server
is tomcat and not the web client end user.
If you decide to allow write access, you should consider placing
the web socket and any applications that utilize it (Display Builder Web Runtime, ...)
behind an authentication layer (Web Proxy, ...) which will limit access
to appropriate users.


Client URLs
-----------

Open the main page of the running instance for explanation
of URLs used to connect to PVs.
Assuming Tomcat on `localhost:8080`, open

    http://localhost:8080/pvws

Web Socket Details
------------------

The basic behavior of the web socket and the fundamental format of the exchanged messages is
not expected to change. Any changes ought to remain compatible by for example adding
message elements which older clients would simply ignore.
There is, however, no guarantee of future compatibility. The exact behavior and message format
is thus only documented on the built-in web page that demonstrates each feature.

To learn about the web socket behavior and the message format, point a web browser to

    http://localhost:8080/pvws

Enable the introspection or debug interface of the web browser. For Firefox, at the time of this
writing, invoke "Inspect" from the context menu, then reload the page to assert that you capture
all network traffic.
You should find a web socket connection to `ws://localhost:8080/pvws/pv`.
Open its Request/Response detail pane in the inspector and try the following key commands.

When you configure tomcat to allow excrypted connections, open web browser to `https://localhost:8080/pvws`
and note that the web socket connection likewise changes to `wss://localhost:8080/pvws/pv`.

**Echo**

Note how pressing the "Echo" button on the web page sends an echo type of message to the web socket,
which then returns the same text.

**Subscribe**

Use the web page to subscribe to for example `sim://sine`.
Note the subscription request sent to the web socket,
and how the web socket then sends 'update' replies.
The web socket sends the complete meta data (units etc.)
just once, followed by only the changed "value" and maybe "severity".
Check the `pvws.js` library as an example for combining the received updates
into a complete value, so end users of the data can always conveniently see
the complete value while the underlying network traffic is optimized to
only transfer changes.

File Layout
===========

Maven layout is based on

    mvn archetype:generate -DgroupId=gov.ornl -DartifactId=pvws -DarchetypeArtifactId=maven-archetype-webapp -DinteractiveMode=false


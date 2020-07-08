PV Web Socket
=============

Web Socket for PVs.

Combines ideas from https://github.com/JeffersonLab/epics2web
with https://github.com/xihui/WebPDA:

 * Web Socket for Channel Access, PV Access, simulated PVs, local PVs, ... based on Phoebus core-pv and RxJava
 * JSON for the data, but packing arrays as binary
 * Metadata is sent once, then only updates. JavaScript client merges updates.


Building
--------

    export ANT_HOME=/path/to/apache-ant
    export CATALINA_HOME=/path/to/apache-tomcat
    export JAVA_HOME=/path/to/jdk8
    export PATH=$ANT_HOME/bin:$JAVA_HOME/bin:$PATH
    
    ant clean war

Running under Tomcat
--------------------

Set the following environment variables, for example in `$CATALINA_HOME/bin/setenv.sh`:

 * `EPICS_CA_ADDR_LIST`: CA address list.
 * `EPICS_CA_MAX_ARRAY_BYTES`: CA array size.
 * `PV_THROTTLE_MS`: Throttle-latest period in milliseconds (default: 1000).
 * `PV_ARRAY_THROTTLE_MS`: .. for arrays (default: 10000).
 * `PV_WRITE_SUPPORT`: Set to `true` to enable writing (default: false).
 
When enabling write access, actual write access is still controlled
on a per-PV basis by Channel Access or PV Access security,
but note that the user and host seen by the CA resp. PVA server
is tomcat and not the web client end user.
 
Place `pvws.war` in `$CATALINA_HOME/webapps`


Client URLs
-----------

Open the main page of the running instance for explanation
of URLs used to connect to PVs.
Assuming Tomcat on `localhost:8080`, open

    http://localhost:8080/pvws
    

Development Status
==================

Uses slightly modified copy of Phoebus core-pv:

 * Builds with Java 8, not depending on Java 9+, yet
 * Allow use without Phoebus's preference handling
 
TODO:

 * Move to Java 11, then use Phoebus core-pv "as is" with PVA, MQTT support


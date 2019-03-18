PV Web Socket
=============

Web Socket for PVs.

Combines ideas from https://github.com/JeffersonLab/epics2web
with https://github.com/xihui/WebPDA:

 * Web Socket for Channel Access, PV Access, simulated PVs, local PVs, ...
 * JSON for the data, but packing arrays as binary
 * Metadata is sent once, then only updates


Building
--------

    export ANT_HOME=/path/to/apache-ant
    export CATALINA_HOME=/path/to/apache-tomcat
    export JAVA_HOME=/path/to/jdk8
    export PATH=$ANT_HOME/bin:$JAVA_HOME/bin:$PATH
    
    ant clean war

Running under Tomcat
--------------------

Set environment variable `EPICS_CA_ADDR_LIST`, for example in `$CATALINA_HOME/bin/setenv.sh`, to the CA address list.
Set environment variable `EPICS_CA_MAX_ARRAY_BYTES` to configure the CA array size.
Set environment variable `PV_THROTTLE_MS` to the throttle-latest period in milliseconds.

Place `pvws.war` in `$CATALINA_HOME/webapps`


Client URLs
-----------

Open the main page of the running instance for explanation
of URLs used to connect to PVs.
Assuming Tomcat on `localhost:8080`, open

    http://localhost:8080/pvws
    

Development Status
==================

Mostly Functional
-----------------

 * Start page that explains API
 * Imported code for VType.PV (ca, sim, loc)


TODO
----

 * Send data to clients
      - Sends initial metadata, then updates. Client lib keeps complete data.
      - JSON, except some binary encoding for array values
 * Clear PVs when session closes or tomcat reloads web application
